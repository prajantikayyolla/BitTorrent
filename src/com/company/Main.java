package com.company;

// "static void main" must be defined in a public class.
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.io.*;
// "static void main" must be defined in a public class.
public class Main {
     static FileWriter fw;
     static BufferedWriter bw;
    public static void write(String message) {
        try{
            // System.out.println(message);
            synchronized (fw) {
                // BufferedWriter bw = new BufferedWriter(fw);
                System.out.print(message);
                bw.write(message);
                bw.newLine();
                // bw.close();
            }
        }
        catch(IOException e) {
            e.printStackTrace();
            System.out.println("Exception occurred");
        }

    }

    public static class MyThread extends Thread {
        public void run(){
            while(true) {
                try{
                    write("thread1 zyxwvut1111111111111111111111111111111111111111111111111111111");
                }
                catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("Exception occurred");
                }

            }

        }
    }
    public static class MyThread1 extends Thread {
        public void run(){
            while(true) {
                try{
                    write("thread2 abcdefghi2222222222222222222222222222222222222222222222222");
                }
                catch(Exception e) {
                    System.out.println("Exception occurred");
                }
            }
        }
    }
    public static void main(String[] args) {
        try {
            fw = new FileWriter("Peer.txt", true);
            bw = new BufferedWriter(fw);
            bw.write("hiii");
             Thread t1 = new MyThread();
             Thread t2 = new MyThread1();
            Thread t11 = new MyThread();
            Thread t22 = new MyThread1();
//             bw.write("hi");
             t1.start();
             t2.start();
            t11.start();
            t22.start();
             Thread.sleep(100);
             t1.stop(); // stopping thread t1
             t2.stop();
            t11.stop(); // stopping thread t1
            t22.stop();
            bw.close();
        }
        catch (Exception e) {
            System.out.println("Exception occurred");
        }

    }
}





//
//public class Main {
//    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
//    public static void main(String[] args) {
//        Thread t1 = new Thread(new Runnable() {
//            int i = 0;
//            public void run() {
//                int i = 0;
//                while(true) {
//                    i++;
//                    if(i% 100000000 == 0)
//                        System.out.print(".");
//                }
//            }
//        });
//        synchronized(t1){
//            t1.start();
//        }
//        final Runnable t2 = new Runnable() {
//            public void run() {
//                try {
//                    t1.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                System.out.println("waiting");
//                notify();
//            }
//        };
//        // t2.start();
//        final ScheduledFuture<?> beeperHandle = scheduler.scheduleAtFixedRate(t2, 3, 3, TimeUnit.SECONDS);
//        scheduler.schedule(new Runnable() {
//            public void run() { beeperHandle.cancel(true); }
//        }, 60 * 60, TimeUnit.SECONDS);
//
////        final ScheduledExecutorService scheduler =
////                Executors.newScheduledThreadPool(1);
////
////        final Runnable beeper = new Runnable() {
////            public void run() { System.out.println("beep"); }
////        };
////        final ScheduledFuture<?> beeperHandle =
////                scheduler.scheduleAtFixedRate(beeper, 10, 10, TimeUnit.SECONDS);
////        scheduler.schedule(new Runnable() {
////            public void run() { beeperHandle.cancel(true); }
////        }, 60 * 60, TimeUnit.SECONDS);
//    }
//}
