package com.redislite;

import com.redislite.persistence.FsyncPolicy;
import java.nio.file.Path;

public record Config(int port, boolean repl, int maxKeys, boolean noAof, Path aofFile,
                     FsyncPolicy aofFsync, long sweepIntervalMillis) {
    public static Config fromArgs(String[] args) { return parse(args); }
    public static Config parse(String[] args) {
        int port=6380,max=0; boolean repl=false,no=false; Path file=Path.of("appendonly.aof");
        FsyncPolicy fs=FsyncPolicy.EVERYSEC; long interval=100;
        for(int i=0;i<args.length;i++) {
            String a=args[i];
            try {
                switch(a) {
                    case "--repl" -> repl=true; case "--no-aof" -> no=true;
                    case "--port" -> port=Integer.parseInt(args[++i]); case "--max-keys" -> max=Integer.parseInt(args[++i]);
                    case "--aof-file" -> file=Path.of(args[++i]); case "--sweep-interval-ms" -> interval=Long.parseLong(args[++i]);
                    case "--aof-fsync" -> fs=FsyncPolicy.valueOf(args[++i].toUpperCase());
                    default -> throw new IllegalArgumentException();
                }
            } catch(Exception e){throw new IllegalArgumentException("invalid arguments",e);}
        }
        if(port<0||port>65535||max<0||interval<=0)throw new IllegalArgumentException("invalid arguments");
        return new Config(port,repl,max,no,file,fs,interval);
    }
}
