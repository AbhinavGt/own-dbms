package com.redislite;

import com.redislite.cli.Repl;
import com.redislite.command.*;
import com.redislite.persistence.*;
import com.redislite.server.TcpServer;
import com.redislite.store.*;
import com.redislite.time.*;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public final class App implements AutoCloseable {
    private final Config config; private final ExpiringLruStore raw; private final MutationLog log;
    private final ExpirySweeper sweeper; private final TcpServer server; private final SynchronizedRequestProcessor processor;
    private final Repl repl;
    private final AofLoader.ReplayResult replayResult;
    public App(Config config) { this(config,new SystemTimeSource()); }
    public App(Config config, TimeSource time) {
        this.config=config; raw=new ExpiringLruStore(time);
        if(config.noAof()) { replayResult=new AofLoader.ReplayResult(0,0,0); log=new NoopLog(); }
        else { replayResult=new AofLoader().replay(config.aofFile(),raw); log=new AofWriter(config.aofFile(),config.aofFsync()); }
        raw.setEvictionListener(log::appendDelete); raw.setMaxKeys(config.maxKeys());
        KeyValueStore store=new PersistentStore(raw,log);
        ReentrantLock lock=new ReentrantLock();
        processor=new SynchronizedRequestProcessor(new DefaultRequestProcessor(new CommandDispatcher(store,time)),lock);
        sweeper=new ExpirySweeper(raw,lock,config.sweepIntervalMillis(),200);
        server=config.repl()?null:new TcpServer(config.port(),processor);
        repl=config.repl()?new Repl(processor,System.in,System.out):null;
    }
    public void start() throws IOException {
        sweeper.start();
        if(server!=null) server.start(); else repl.run();
    }
    public int port(){return server==null?-1:server.getPort();}
    public AofLoader.ReplayResult replayResult(){return replayResult;}
    public SynchronizedRequestProcessor processor(){return processor;}
    public void stop(){if(server!=null)server.stop();sweeper.stop();log.close();}
    @Override public void close(){stop();}
    private static final class NoopLog implements MutationLog {
        public void appendSet(String k,String v){} public void appendDelete(String k){} public void appendExpireAt(String k,long t){}
        public boolean isHealthy(){return true;} public void close(){}
    }
}
