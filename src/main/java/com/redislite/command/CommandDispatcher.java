package com.redislite.command;

import com.redislite.protocol.ParsedCommand;
import com.redislite.store.KeyValueStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.redislite.time.*;

/** Dispatches parsed commands to storage-backed handlers. */
public final class CommandDispatcher {
    private final KeyValueStore store;
    private final Map<String, CommandHandler> handlers = new HashMap<>();
    private final TimeSource time;

    public CommandDispatcher(KeyValueStore store) {
        this(store, new SystemTimeSource());
    }
    public CommandDispatcher(KeyValueStore store, TimeSource time) {
        this.store = store;
        this.time = time;
        handlers.put("SET", this::set);
        handlers.put("GET", this::get);
        handlers.put("DEL", this::delete);
        handlers.put("EXISTS", this::exists);
        handlers.put("PING", this::ping);
        handlers.put("QUIT", this::quit);
        handlers.put("EXPIRE", this::expire);
        handlers.put("TTL", this::ttl);
        handlers.put("DBSIZE", this::dbsize);
    }

    public String execute(ParsedCommand command) {
        if (command == null) {
            return "ERR empty command";
        }
        CommandHandler handler = handlers.get(command.name());
        return handler == null
                ? "ERR unknown command '" + command.name() + "'"
                : handler.handle(command.args());
    }

    private String set(List<String> args) {
        if (args.size() != 2) return wrongArgs("SET");
        store.set(args.get(0), args.get(1));
        return "OK";
    }

    private String get(List<String> args) {
        if (args.size() != 1) return wrongArgs("GET");
        return store.get(args.get(0)).orElse("(nil)");
    }

    private String delete(List<String> args) {
        if (args.size() != 1) return wrongArgs("DEL");
        return store.delete(args.get(0)) ? "1" : "0";
    }

    private String exists(List<String> args) {
        if (args.size() != 1) return wrongArgs("EXISTS");
        return store.exists(args.get(0)) ? "1" : "0";
    }

    private String ping(List<String> args) {
        return args.isEmpty() ? "PONG" : wrongArgs("PING");
    }

    private String quit(List<String> args) {
        return "BYE";
    }
    private String expire(List<String> args) {
        if(args.size()!=2)return wrongArgs("EXPIRE");
        long seconds;
        try { seconds=Long.parseLong(args.get(1)); } catch(NumberFormatException e){return "ERR value is not an integer or out of range";}
        if(seconds<=0)return "ERR invalid expire time";
        try { return store.setExpiryAt(args.get(0),Math.addExact(time.nowMillis(),Math.multiplyExact(seconds,1000)))?"1":"0"; }
        catch(ArithmeticException e){return "ERR invalid expire time";}
    }
    private String ttl(List<String> args) {
        if(args.size()!=1)return wrongArgs("TTL");
        long ms=store.ttlMillis(args.get(0)); if(ms<0)return Long.toString(ms);
        return Long.toString(ms / 1000 + (ms % 1000 == 0 ? 0 : 1));
    }
    private String dbsize(List<String> args) { return args.isEmpty()?Integer.toString(store.size()):wrongArgs("DBSIZE"); }

    private static String wrongArgs(String command) {
        return "ERR wrong number of arguments for '" + command + "'";
    }
}
