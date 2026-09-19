package com.redislite.command;

import com.redislite.protocol.ParsedCommand;
import com.redislite.store.KeyValueStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dispatches parsed commands to storage-backed handlers. */
public final class CommandDispatcher {
    private final KeyValueStore store;
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public CommandDispatcher(KeyValueStore store) {
        this.store = store;
        handlers.put("SET", this::set);
        handlers.put("GET", this::get);
        handlers.put("DEL", this::delete);
        handlers.put("EXISTS", this::exists);
        handlers.put("PING", this::ping);
        handlers.put("QUIT", this::quit);
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

    private static String wrongArgs(String command) {
        return "ERR wrong number of arguments for '" + command + "'";
    }
}
