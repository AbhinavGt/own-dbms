package com.redislite.cli;

import com.redislite.command.CommandDispatcher;
import com.redislite.command.DefaultRequestProcessor;
import com.redislite.command.RequestProcessor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Runs the line-oriented command prompt over injected streams. */
public final class Repl {
    private final RequestProcessor processor;
    private final InputStream input;
    private final OutputStream output;
    public Repl(CommandDispatcher dispatcher, InputStream input, OutputStream output) {
        this(new DefaultRequestProcessor(dispatcher), input, output);
    }

    public Repl(RequestProcessor processor, InputStream input, OutputStream output) {
        this.processor = processor;
        this.input = input;
        this.output = output;
    }

    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            String line;
            while (true) {
                writer.print("> ");
                writer.flush();
                line = reader.readLine();
                if (line == null) break;
                var reply = processor.process(line);
                if (reply.text() != null) writer.println(reply.text());
                if (reply.close()) break;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("I/O error in REPL", exception);
        }
    }
}
