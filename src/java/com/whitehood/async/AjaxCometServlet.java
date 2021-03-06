package com.whitehood.async;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = {"/chat"}, asyncSupported = true)
public class AjaxCometServlet extends HttpServlet {

    private static final BlockingQueue<String> messageQueue = 
            new LinkedBlockingQueue<String>();
    private static final Queue<AsyncContext> queue = 
            new ConcurrentLinkedQueue<AsyncContext>();  
    private Thread notifierThread = null;

    private static final String BEGIN_SCRIPT_TAG = "<script "
                                +"type='text/javascript'>\n";
    private static final String END_SCRIPT_TAG = "</script>\n";
    private static final long serialVersionUID = -2919167206889576860L;
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        Runnable notifierRunnable = new Runnable() {
            public void run() {
                boolean done = false;
                while (!done) {
                    String cMessage = null;
                    try {
                        cMessage = messageQueue.take();
                        for (AsyncContext ac : queue) {
                            try {
                                PrintWriter acWriter = 
                                        ac.getResponse().getWriter();
                                acWriter.println(cMessage);
                                acWriter.flush();
                            } catch(IOException ex) {
                                System.out.println(ex);
                                queue.remove(ac);
                            }
                        }
                    } catch(InterruptedException iex) {
                        done = true;
                        System.out.println(iex);
                    }
                }
            }
        };
        notifierThread = new Thread(notifierRunnable);
        notifierThread.start();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setContentType("text/html");
        res.setHeader("Cache-Control", "private");
        res.setHeader("Pragma", "no-cache");
       
        final AsyncContext ac = req.startAsync();
        ac.setTimeout(10 * 60 * 1000);
        ac.addListener(new AsyncListener() {
            public void onStartAsync(AsyncEvent event) throws IOException {
            }
            public void onComplete(AsyncEvent event) throws IOException {
                queue.remove(ac);
            }
            public void onTimeout(AsyncEvent event) throws IOException {
                queue.remove(ac);
            }
            public void onError(AsyncEvent event) throws IOException {
                queue.remove(ac);
            }
        });
        queue.add(ac);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse res) 
            throws ServletException, IOException {
        res.setContentType("text/plain");
        res.setHeader("Cache-Control", "private");
        res.setHeader("Pragma", "no-cache");

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");
        String name = req.getParameter("name");

        if ("login".equals(action)) {
            String cMessage = BEGIN_SCRIPT_TAG + toJsonp("Message", name 
                    + " has joined this chat.") + END_SCRIPT_TAG;
            notify(cMessage);

            res.getWriter().println("success");
        } else if ("post".equals(action)) {
            String message = req.getParameter("message");
            String cMessage = BEGIN_SCRIPT_TAG + toJsonp(name, message) 
                    + END_SCRIPT_TAG;
            notify(cMessage);

            res.getWriter().println("success");
        } else {
            res.sendError(422, "Unprocessable Entity");
        }
    }

    @Override
    public void destroy() {
        queue.clear();
        notifierThread.interrupt();
    }

    private void notify(String cMessage) throws IOException {
        try {
            messageQueue.put(cMessage);
        } catch(Exception ex) {
            IOException t = new IOException();
            t.initCause(ex);
            throw t;
        }
    }

    private String escape(String orig) {
        StringBuffer buffer = new StringBuffer(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
            case '\r': // contiune
                break;
            case '\f':
                buffer.append("\\f");
                break;
            case '\b':
                buffer.append("\\b");
                break;
            case '\n':
                buffer.append("<br />");
                break;
            case '\t':
                buffer.append("\\t");
                break;
            case '\'':
                buffer.append("\\'");
                break;
            case '\"':
                buffer.append("\\\"");
                break;
            case '\\':
                buffer.append("\\\\");
                break;
            case '&':
                buffer.append("&amp;");
                break;
            case '<':
                buffer.append("&lt;");
                break;
            case '>':
                buffer.append("&gt;");
                break;
            default:
                buffer.append(c);
            }
        }
        return buffer.toString();
    }

    private String toJsonp(String name, String message) {
        return "window.parent.app.update({ name: \"" + escape(name) 
                + "\", message: \"" + escape(message) + "\" });\n";
    }
}
