package com.redislite.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class AofWriter implements MutationLog {
    private static final Logger LOG=Logger.getLogger(AofWriter.class.getName());
    private final FileChannel channel; private final FsyncPolicy policy; private final AtomicBoolean dirty=new AtomicBoolean();
    private final AtomicLong syncCount=new AtomicLong(); private volatile boolean healthy=true, closed; private ScheduledExecutorService syncer;
    public AofWriter(Path file,FsyncPolicy policy) {
        try { Path parent=file.toAbsolutePath().getParent(); if(parent!=null) Files.createDirectories(parent); channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND); }
        catch(IOException e){throw new PersistenceException("Unable to open AOF",e);} this.policy=policy;
        if(policy==FsyncPolicy.EVERYSEC){syncer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"aof-fsync");t.setDaemon(true);return t;});syncer.scheduleWithFixedDelay(this::syncTick,1,1,TimeUnit.SECONDS);}
    }
    public long syncCount(){return syncCount.get();}
    public long getSyncCount(){return syncCount();}
    @Override public boolean isHealthy(){return healthy;}
    @Override public synchronized void appendSet(String k,String v){append("SET "+validateKey(k)+" "+validateValue(v)+"\n");}
    @Override public synchronized void appendDelete(String k){append("DEL "+validateKey(k)+"\n");}
    @Override public synchronized void appendExpireAt(String k,long at){append("PEXPIREAT "+validateKey(k)+" "+at+"\n");}
    private String validateKey(String k){if(k==null||k.isEmpty()||k.chars().anyMatch(Character::isWhitespace)||k.indexOf('\r')>=0||k.indexOf('\n')>=0)throw new IllegalArgumentException("invalid key");return k;}
    private String validateValue(String v){if(v==null||v.indexOf('\r')>=0||v.indexOf('\n')>=0)throw new IllegalArgumentException("invalid value");return v;}
    private void append(String line){
        if(!healthy||closed) { healthy=false; throw new PersistenceException("AOF is unhealthy"); }
        try { ByteBuffer b=ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)); while(b.hasRemaining()) channel.write(b); if(policy==FsyncPolicy.ALWAYS) force(); else if(policy==FsyncPolicy.EVERYSEC) dirty.set(true); }
        catch(IOException e){fail(e);}
    }
    private void syncTick(){if(dirty.compareAndSet(true,false))try{force();}catch(PersistenceException ignored){}}
    private void force(){try{channel.force(false);syncCount.incrementAndGet();}catch(IOException e){fail(e);}}
    private void fail(IOException e){healthy=false;LOG.severe("AOF failure: "+e);throw new PersistenceException("AOF failure",e);}
    @Override public synchronized void close(){if(closed)return;closed=true;if(syncer!=null){syncer.shutdownNow();try{syncer.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}try{if(healthy)force();channel.close();}catch(IOException e){healthy=false;LOG.severe("AOF close failure: "+e);}}
}
