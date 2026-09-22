package com.redislite.bench;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Small closed-loop benchmark client for the line protocol. */
public final class BenchmarkClient {
    public static void main(String[] args) throws Exception {
        Map<String,String> o=parse(args); String host=o.getOrDefault("--host","127.0.0.1");
        int port=Integer.parseInt(o.getOrDefault("--port","6380")), requests=Integer.parseInt(o.getOrDefault("--requests","200000"));
        int clients=Integer.parseInt(o.getOrDefault("--clients","50")); String workload=o.getOrDefault("--workload","mixed");
        ExecutorService pool=Executors.newFixedThreadPool(clients); CountDownLatch ready=new CountDownLatch(clients),go=new CountDownLatch(1);
        List<Long> lat=Collections.synchronizedList(new ArrayList<>()); long start=System.nanoTime();
        for(int c=0;c<clients;c++){final int id=c;pool.submit(()->{try(Socket s=new Socket(host,port);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));BufferedWriter w=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8))){ready.countDown();go.await();int n=requests/clients+(id<requests%clients?1:0);for(int i=0;i<n;i++){String cmd=workload.equals("get")||workload.equals("mixed")&&i%5!=0?"GET k"+(i%1000):"SET k"+(i%1000)+" v";long t=System.nanoTime();w.write(cmd+"\n");w.flush();String reply=r.readLine();if(reply==null||reply.startsWith("ERR"))throw new IOException("unexpected reply "+reply);lat.add(System.nanoTime()-t);}}catch(Exception e){throw new CompletionException(e);}});}
        ready.await();go.countDown();pool.shutdown();pool.awaitTermination(1,TimeUnit.HOURS);long elapsed=System.nanoTime()-start;long[] a=lat.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf("requests=%d throughput=%.2f ops/s p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms%n",a.length,a.length/(elapsed/1e9),pct(a,.50)/1e6,pct(a,.95)/1e6,pct(a,.99)/1e6,(a.length==0?0:a[a.length-1]/1e6));
    }
    private static long pct(long[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)(a.length*p))];}
    private static Map<String,String> parse(String[] a){Map<String,String> m=new HashMap<>();for(int i=0;i<a.length;i++)if(a[i].startsWith("--"))m.put(a[i],i+1<a.length&&!a[i+1].startsWith("--")?a[++i]:"true");return m;}
}
