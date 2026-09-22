package com.redislite.persistence;

import com.redislite.store.ExpiringLruStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public final class AofLoader {
    private static final Logger LOG=Logger.getLogger(AofLoader.class.getName());
    public record ReplayResult(long recordsApplied,long tornBytesTruncated,long elapsedMillis){}
    public ReplayResult replay(Path file, ExpiringLruStore store) {
        long start=System.currentTimeMillis();
        if(!Files.exists(file)) return new ReplayResult(0,0,0);
        try {
            byte[] data=Files.readAllBytes(file); if(data.length==0)return new ReplayResult(0,0,0);
            long valid=0, applied=0; int lineNo=0, begin=0;
            CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            for(int i=0;i<data.length;i++) if(data[i]=='\n') {
                lineNo++; byte[] line=Arrays.copyOfRange(data,begin,i);
                try { String s=decoder.decode(ByteBuffer.wrap(line)).toString(); apply(s,store,lineNo); applied++; }
                catch(AofCorruptException e){throw e;} catch(Exception e){throw new AofCorruptException("Corrupt AOF at line "+lineNo,e);}
                begin=i+1; valid=begin; decoder.reset();
            }
            long torn=data.length-valid;
            if(torn>0){try(FileChannel c=FileChannel.open(file,StandardOpenOption.WRITE)){c.truncate(valid);c.force(true);}LOG.warning("Truncated "+torn+" torn AOF bytes");}
            return new ReplayResult(applied,torn,System.currentTimeMillis()-start);
        } catch(IOException e){throw new PersistenceException("Unable to read AOF",e);}
    }
    private void apply(String s,ExpiringLruStore store,int line) {
        String[] p=s.split(" ",3);
        try {
            if(p.length>=1&&"SET".equals(p[0])&&p.length==3) store.set(p[1],p[2]);
            else if(p.length==2&&"DEL".equals(p[0])) store.delete(p[1]);
            else if(p.length==3&&"PEXPIREAT".equals(p[0])) store.setExpiryAt(p[1],Long.parseLong(p[2]));
            else throw new IllegalArgumentException("invalid record");
        } catch(RuntimeException e){throw new AofCorruptException("Corrupt AOF at line "+line,e);}
    }
}
