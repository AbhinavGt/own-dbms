package com.redislite.bench;
import com.redislite.persistence.*; import com.redislite.store.*; import com.redislite.time.*;
import java.nio.file.*; 
public final class RecoveryBenchmark {
 public static void main(String[] args)throws Exception{Path p=Path.of(args.length>0?args[0]:"bench-data/recovery.aof");int n=args.length>1?Integer.parseInt(args[1]):100000;Files.deleteIfExists(p);try(AofWriter w=new AofWriter(p,FsyncPolicy.NO)){for(int i=0;i<n;i++)w.appendSet("k"+i,"value");}long t=System.nanoTime();var r=new AofLoader().replay(p,new ExpiringLruStore(new SystemTimeSource()));System.out.printf("records=%d bytes=%d replayMs=%d records/s=%.0f%n",r.recordsApplied(),Files.size(p),r.elapsedMillis(),n/((System.nanoTime()-t)/1e9));}
}
