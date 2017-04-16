package za.co.pas.lib.multidownloader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import za.co.pas.lib.multidownloader.logging.LogListener;
import za.co.pas.lib.multidownloader.logging.MDLogFormatter;

/**
 *
 * @author Andre Labuschagne
 */
public class MultiDownloader {

//    private static final Logger LOG = Logger.getLogger(MultiDownloader.class.getName());

    private long count256Kb = 0;
    private static final String[] DOTS = {"\u2654", "\u2655", "\u2656", "\u2657", "\u2658", "\u2659", "\u265a", "\u265b", "\u265c", "\u265d", "\u265e", "\u265f"};
    private int dotIndex = 0;
    private long totalSize = 0l;
    private long millis = 0;
    private String strTotal;
    private final Object LOCK_COUNTER256 = new Object();
    private String ETag;
    private final URL url;
    private int numberOfParts = 4;
    private int bufferSize = 8 * 1024;
    private final Map<Integer, StringBuilder> threadOutputs = new TreeMap<>();
    private String path;
    private LogListener logListener;
  

    public static String supportMulti(HttpURLConnection urlConnection) {
        if ((urlConnection.getHeaderField("Accept-Ranges") != null)
                && (urlConnection.getHeaderField("Accept-Ranges").equals("bytes"))) {
            return urlConnection.getHeaderField("ETag");
        }
        return null;
    }

    private MultiDownloader() {
        url = null;
    }

    /**
     *
     * @param url The URL of the file to be downloaded
     * @param parts How many parts / threads to use when downloading, 4 is a
     * good number
     * @param bufferSize The buffer size for the concatenating read / write, a
     * good number is 8192
     */
    public MultiDownloader(URL url, int parts, int bufferSize) {
        this.url = url;
        this.numberOfParts = parts;
        this.bufferSize = bufferSize;
        this.path = "." + File.separator;
    }

    /**
     *
     * @param url The URL of the file to be downloaded
     * @param path The path for the downloaded file (and interim parts), should
     * just be the directory
     * @param parts How many parts / threads to use when downloading, 4 is a
     * good number
     * @param bufferSize The buffer size for the concatenating read / write, a
     * good number is 8192
     */
    public MultiDownloader(URL url, String path, int parts, int bufferSize) {
        this.url = url;
        this.numberOfParts = parts;
        this.bufferSize = bufferSize;
        this.path = path;
        if (!this.path.endsWith(File.separator)) {
            this.path += File.separator;
        }
    }    

    /**
     *
     * @param url The URL of the file to be downloaded
     * @param path The path for the downloaded file (and interim parts), should
     * just be the directory
     * @param parts How many parts / threads to use when downloading, 4 is a
     * good number
     * @param bufferSize The buffer size for the concatenating read / write, a
     * good number is 8192
     */
    public MultiDownloader(URL url, StringBuilder path, int parts, int bufferSize) {
        this.url = url;
        this.numberOfParts = parts;
        this.bufferSize = bufferSize;
        this.path = path.toString();
        if (!this.path.endsWith(File.separator)) {
            this.path += File.separator;
        }
    }

    private void logInfo(String s, String... more) {
        if(logListener != null) {
            logListener.logInfo(s, more);
        }
    }

    private void logWarn(String s, String... more) {
        if(logListener != null) {
            logListener.logWarn(s, more);
        }
    }

    private void logError(String s, String... more) {
        if(logListener != null) {
            logListener.logError(s, more);
        }
    }
    /**
     * 
     * @return The absolute path (including the file name) of the downloaded file, after it was concatenated, or null if an error occurred.
     */
    public String go() {
        long startTime = System.currentTimeMillis();
        logInfo("Fetching file size...");
        long totalTotalSize = 0L;
        try {
            totalSize = 0L;
            List<DownloadRunner> listDownloaders = new LinkedList<>();
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            int status = ((HttpURLConnection) urlConnection).getResponseCode();
            ETag = supportMulti(urlConnection);
            urlConnection.disconnect();
            if (status == 200) {
                totalTotalSize = totalSize = urlConnection.getContentLengthLong();
                logInfo("Downloading {0}", humanReadableByteCount(totalSize, false));
                //Check free space > (totalSize * 2)
                if (!enoughSpace(totalSize)) {
                    logWarn("Not enough free space to download {0}", humanReadableByteCount(totalSize, false));
                    return null;
                }
                if (ETag == null) {
                    logWarn("Server does not support partial downloads, using 1 part download");
                    DownloadRunner dl = new DownloadRunner(url, path, -1L, -1L, -1, ETag);
                    listDownloaders.add(dl);
                    Thread t = new Thread(dl);
                    t.start();
                } else {
                    long chunkSize = totalSize / numberOfParts;
                    long start = 0;
                    for (int i = 0; i < numberOfParts; i++) {
                        long end = start + chunkSize;
                        if (i == numberOfParts - 1) {
                            end = totalSize;
                        }
//                        StringBuilder sbLog = new StringBuilder("Start-End:");
//                        sbLog.append(start).append("-").append(end).append(" size: ").append(end - start);
//                        logInfo(sbLog.toString());
                        DownloadRunner dl = new DownloadRunner(url, path, start, end, i, ETag);
                        listDownloaders.add(dl);
                        Thread t = new Thread(dl);
                        t.start();
                        start += chunkSize + 1;
                    }
                }
                boolean running = true;
                while (running) {
                    Iterator<DownloadRunner> it = listDownloaders.listIterator();
                    while (it.hasNext()) {
                        DownloadRunner dl = it.next();
                        if (!dl.isRunning()) {
                            it.remove();
                        }
                    }
                    running = !listDownloaders.isEmpty();
                    try {
                        TimeUnit.MILLISECONDS.sleep(1);
                    } catch (InterruptedException ex) {
                        logError("Interrupted: {0}", ex.toString());
                    }
                }
                logoutThreadOutputs();
                //put it back together
                if (ETag != null) {
                    long startConcat = System.currentTimeMillis();
                    logInfo("Concatenating file parts...");
                    String fileName = FilenameUtils.getName(url.getPath());
                    File finalFile = new File(path + fileName);
                    try (FileOutputStream fos = new FileOutputStream(finalFile);
                            FileChannel fcfos = fos.getChannel();) {
                        /**
                         * NEW *
                         */
                        for (int i = 0; i < numberOfParts; i++) {
                            File filePart = new File(path + fileName + "." + i);
                            logInfo("... Reading {0}", filePart.getName());
                            count256Kb = 0;
                            totalSize = filePart.length();
                            try (FileInputStream fis = new FileInputStream(filePart);
                                    FileChannel fcfis = fis.getChannel();) {
                                ByteBuffer bb = ByteBuffer.allocateDirect(bufferSize);
                                int nRead;
                                while ((nRead = fcfis.read(bb)) != -1) {
                                    if (nRead == 0) {
                                        continue;
                                    }
                                    bb.position(0);
                                    bb.limit(nRead);
                                    fcfos.write(bb);
                                    bb.clear();
                                }
                            } catch (IOException e) {
                                logError("Error while reading file: {0}", e.toString());
                                return null;
                            }
                            FileUtils.forceDelete(filePart);
                            try {
                                TimeUnit.MILLISECONDS.sleep(1);
                            } catch (InterruptedException ex) {
                                logError("InterruptedException: {0}", ex.toString());
                            }
                        }
                        logInfo("Done");
                        return finalFile.getAbsolutePath();
                    } catch (IOException e) {
                        logError("Error while combining file: {0}", e.toString());
                    } finally {
                        long duration = System.currentTimeMillis() - startConcat;
                        logInfo("Concatenating of file parts took {0}", calcTimeLaps(duration, false));
                    }
                } else {
                    //Single file
                    String fileName = FilenameUtils.getName(url.getPath());
                    File finalFile = new File(path + fileName);
                    logInfo("Done");
                    return finalFile.getAbsolutePath();                
                }
            } else {
                logWarn("Could not check file: HTTP Status Code = {0}", Integer.toString(status));
                return null;
            }
        } catch (MalformedURLException e) {
            logError("Error checking file: {0}", e.toString());
            return null;
        } catch (IOException e) {
            logError("Error checking file: {0}", e.toString());
            return null;
        } finally {
            if (totalTotalSize > 0L) {
                long duration = System.currentTimeMillis() - startTime;
                double kilobytesPerSecond = (double) (totalTotalSize / 1024.0) / ((double) duration / 1000.0);//kb / s
                StringBuilder sbLog = new StringBuilder("[Total process] Downloaded ");
                sbLog.append(humanReadableByteCount(totalTotalSize, false)).append(" @ ");
                sbLog.append(String.format("%.3f", kilobytesPerSecond)).append(" KiB/s");
                logInfo(sbLog.toString());
            }
        }
        return null;
    }

    /**
     * Helper method. Check to see if there is enough space to download the file
     *
     * @param fileSize
     * @return
     */
    private boolean enoughSpace(long fileSize) {
        File file = new File(".");
        long totalSpace = file.getTotalSpace(); //total disk space in bytes.
        long usableSpace = file.getUsableSpace(); ///unallocated / free disk space in bytes.
        long freeSpace = file.getFreeSpace(); //unallocated / free disk space in bytes.

        return (freeSpace > fileSize * 2L);
    }

    /**
     * Helper method
     *
     * @param sb
     * @param currentMb
     * @param totalMb
     */
    private void addSpaces(StringBuilder sb, long currentMb, long totalMb) {
        String cMb = Long.toString(currentMb);
        String tMb = Long.toString(totalMb);
        int size = tMb.length() - cMb.length();
        if (size <= 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            sb.append(" ");
        }
    }

    /**
     *
     * @param sb
     * @param cMb
     * @param tMb
     */
    private void addSpaces(StringBuilder sb, String cMb, String tMb) {
        int size = tMb.length() - cMb.length();
        if (size <= 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            sb.append(" ");
        }
    }

    /**
     *
     */
    private void doLogOutput() {
        synchronized (LOCK_COUNTER256) {
            if (count256Kb == 0) {
                logInfo("Total: {0}", Long.toString(totalSize));
                millis = System.currentTimeMillis();
                strTotal = humanReadableByteCount(totalSize, false);
                StringBuilder sb = new StringBuilder(MDLogFormatter.NO_PRINTLINE);
                addSpaces(sb, 0, totalSize / (1024 * 1024));
                sb.append("0MB|");
                logInfo(sb.toString());
            }
            count256Kb++;
            dotIndex++;
            if (dotIndex >= DOTS.length) {
                dotIndex = 0;
            }
        }
        //Do the dots
        String strLog = MDLogFormatter.NO_PRINTLINE + DOTS[dotIndex];
        logInfo(strLog);//"\u2730");

        if (count256Kb % 4L == 0) {//every 1Mb
            strLog = MDLogFormatter.NO_PRINTLINE + " ";
            logInfo(strLog);
        }

        if (count256Kb % 40L == 0) {//10Mb report
            long duration = System.currentTimeMillis() - millis;
            millis = System.currentTimeMillis();
            double kilobytesPerSecond = (10.0 * 1024.0) / ((double) duration / 1000.0);//kb / s
//                        durationEntries.add(kilobytesPerSecond);
//                        setKbps(kilobytesPerSecond);
            String strCount = humanReadableByteCount(count256Kb * 256L * 1024L, false);
            StringBuilder sbLog = new StringBuilder(MDLogFormatter.NO_PRINTLINE).append(" ");
            addSpaces(sbLog, strCount, strTotal);
            sbLog.append(strCount).append(" ");
            sbLog.append(String.format("%.3f", kilobytesPerSecond)).append(" KiB/s [");
            //calc estimated time left
            //kb/s => b/ms
            //how many bytes are left...
            long bytesLeft = totalSize - (count256Kb * 256L * 1024L);
            if (bytesLeft > 0L) {
                double bytesPerSecond = kilobytesPerSecond * 1024.0;
                double bytesPerMillisecond = bytesPerSecond / 1000.0;
                long milliSecondsLeft = (long) ((double) bytesLeft / bytesPerMillisecond);
                sbLog.append(calcTimeLaps(milliSecondsLeft, false));
            } else {
                sbLog.append("-");
            }
            sbLog.append("]\n");
            addSpaces(sbLog, (count256Kb * 256L * 1024L) / (1024 * 1024), totalSize / (1024 * 1024));
            sbLog.append((count256Kb * 256L * 1024L) / (1024 * 1024)).append("MB|");
            logInfo(sbLog.toString());
        }
    }

    /**
     * The inner class
     */
    private class DownloadRunner implements Runnable {

        private final URL url;
        private final long startByte;
        private final long endByte;
        private final int partNumber;
        private final String ETag;
        private final Random random;
        private boolean running;
        private long totalSize;
        private String path;

        private DownloadRunner() {
            url = null;
            startByte = -1L;
            endByte = -1L;
            partNumber = 0;
            ETag = null;
            random = new Random(System.currentTimeMillis());
            path = null;
        }

        public DownloadRunner(URL url, String path, long startByte, long endByte, int partNumber, String ETag) {
            this.url = url;
            this.startByte = startByte;
            this.endByte = endByte;
            this.partNumber = partNumber;
            this.ETag = ETag;
            random = new Random(System.currentTimeMillis());
            this.path = path;

            if (!this.path.endsWith(File.separator)) {
                this.path += File.separator;
            }
        }

        @Override
        public void run() {
            setRunning(true);
            String fileName = FilenameUtils.getName(url.getPath());
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(2 * 60 * 1000);//2 minutes
                urlConnection.setReadTimeout(2 * 60 * 1000);
                urlConnection.setRequestProperty("User-Agent", "MultiDownloader 0.0.1");
                if (ETag != null) {
                    urlConnection.setRequestProperty("If-Range", ETag);
                    if (startByte != -1L) {
                        StringBuilder range = new StringBuilder("bytes=");
                        range.append(startByte).append("-").append(endByte);
                        urlConnection.setRequestProperty("Range", range.toString());
                    }
                }
                urlConnection.connect();
                int status = urlConnection.getResponseCode();
                if ((status == 200) || (status == 206)) {
                    //File name
                    StringBuilder sbLog = new StringBuilder("[");
                    sbLog.append(fileName);
                    if (partNumber > -1) {
                        sbLog.append(" (").append(partNumber).append(")");
                    }
                    sbLog.append("] Starting download of ");

//                    List<Double> durationEntries = new LinkedList<>();
                    //                    long millis = System.currentTimeMillis();
                    long millisStart = System.currentTimeMillis();
                    StringBuilder fileNamePart = new StringBuilder(path).append(fileName);
                    if (partNumber >= 0) {
                        fileNamePart.append(".").append(partNumber);
                    }
                    File file = new File(fileNamePart.toString());
                    try (BufferedInputStream bis = new BufferedInputStream(urlConnection.getInputStream());
                            FileOutputStream fos = new FileOutputStream(file);) {
                        byte[] bytes = new byte[1 * 1024];//1Kb
                        int read;
                        long count = 0L;
                        if (startByte == -1L) {
                            totalSize = urlConnection.getContentLengthLong();
                        } else {
                            totalSize = endByte - startByte;
                        }
                        sbLog.append(humanReadableByteCount(totalSize, false));

                        if (ETag != null) {
                            sbLog.append(" (").append(humanReadableByteCount(startByte, false)).append(" - ").append(humanReadableByteCount(endByte, false)).append(")");
                        }
//                        logInfo(sbLog.toString());
                        addToThreadOutputs(partNumber, sbLog);

                        while (true) {
                            read = bis.read(bytes);
                            if (read == -1) {
                                break;
                            }
                            count += read;
                            if (count % (256L * 1024L) == 0) {//Every 256kB
                                doLogOutput();
                            }

                            if (count % (2L * 1024L) == 0) {//rest every 2kb for 2 millisecond
                                //To throttle the cpu usage
                                //1-2 milliseconds
                                try {
                                    TimeUnit.MILLISECONDS.sleep(random.nextInt(1) + 1);
                                } catch (InterruptedException ex) {
                                    logError("InterruptedException: {0}", ex.toString());
                                }
                            }
                            fos.write(bytes, 0, read);
                        }
                        //                            if (isAbort()) {
                        //                                logWarn("Aborting...");
                        //                                return false;
                        //                            }
                        long millisEnd = System.currentTimeMillis();
                        sbLog = new StringBuilder("\n[");
                        sbLog.append(fileName);
                        if (partNumber > -1) {
                            sbLog.append(" (").append(partNumber).append(")");
                        }
                        sbLog.append("] Downloaded: ");
                        sbLog.append(humanReadableByteCount(count, false));
                        sbLog.append(". Total time: ");
                        sbLog.append(calcTimeLaps(millisEnd - millisStart, true)).append(", Average speed: ");
                        double kilobytesPerSecond = ((double) count / 1024.0) / ((double) (millisEnd - millisStart) / 1000.0);
                        sbLog.append(String.format("%.3f", kilobytesPerSecond)).append(" KiB/s\n\n");
//                        logInfo(sbLog.toString());
                        fos.flush();
                        addToThreadOutputs(partNumber, sbLog);
                    } catch (Exception e) {
                        sbLog = new StringBuilder("[");
                        sbLog.append(fileName);
                        if (partNumber > -1) {
                            sbLog.append(" (").append(partNumber).append(")");
                        }
                        sbLog.append("] Error while downloading file: {0}");
                        logError(sbLog.toString(), e.toString());
                    }
                } else {
                    StringBuilder sbLog = new StringBuilder("[");
                    sbLog.append(fileName);
                    if (partNumber > -1) {
                        sbLog.append(" (").append(partNumber).append(")");
                    }
                    sbLog.append("] Response code is not 200 for ");
                    sbLog.append(url.toString()).append(" : ").append(status);
                    logWarn(sbLog.toString());
                }
            } catch (IOException e) {
                StringBuilder sbLog = new StringBuilder("[");
                sbLog.append(fileName);
                if (partNumber > -1) {
                    sbLog.append(" (").append(partNumber).append(")");
                }
                sbLog.append("] Error while downloading file: {0}");
                logError(sbLog.toString(), e.toString());
            }
            setRunning(false);
        }

        /**
         * @return the running
         */
        public synchronized boolean isRunning() {
            return running;
        }

        /**
         * @param running the running to set
         */
        public synchronized void setRunning(boolean running) {
            this.running = running;
        }
    }

    private void addToThreadOutputs(int partNumber, StringBuilder sbLog) {
        synchronized (threadOutputs) {

            if (threadOutputs.containsKey(partNumber)) {
                StringBuilder log = threadOutputs.get(partNumber);
                log.append("\n\t").append(sbLog);
            } else {
                threadOutputs.put(partNumber, sbLog);
            }
        }
    }

    private void logoutThreadOutputs() {
        synchronized (threadOutputs) {
            String log = MDLogFormatter.NO_PRINTLINE + "\n";
            logInfo(log);
            threadOutputs.keySet().stream().map((i) -> threadOutputs.get(i)).forEachOrdered((sb) -> {
                logInfo(sb.toString());
            });
        }
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private String calcTimeLaps(long msLeft, boolean longTimeFormat) {
        Period period = new Period(msLeft);
        PeriodFormatter daysHoursMinutes;
        if (longTimeFormat) {
            daysHoursMinutes = new PeriodFormatterBuilder()
                    .appendYears()
                    .appendSuffix(" year", " years")
                    .appendSeparator(", ")
                    .appendMonths()
                    .appendSuffix(" month", " months")
                    .appendSeparator(", ")
                    .appendWeeks()
                    .appendSuffix(" week", " weeks")
                    .appendSeparator(", ")
                    .appendDays()
                    .appendSuffix(" day", " days")
                    .appendSeparator(" and ")
                    .appendHours()
                    .appendSuffix(" hour", " hours")
                    .appendSeparator(", ")
                    .appendMinutes()
                    .appendSuffix(" minute", " minutes")
                    .appendSeparator(", ")
                    .appendSeconds()
                    .appendSuffix(" second", " seconds")
                    .appendSeparator(", ")
                    .appendMillis()
                    .appendSuffix(" millisecond", " milliseconds")
                    .toFormatter();
        } else {
            daysHoursMinutes = new PeriodFormatterBuilder()
                    .appendYears()
                    .appendSeparator("-")
                    .appendMonths()
                    .appendSeparator("-")
                    .appendWeeks()
                    .appendSeparator("-")
                    .appendDays()
                    .appendSeparator(" ")
                    .appendHours()
                    .appendSeparator(":")
                    .appendMinutes()
                    .appendSeparator(":")
                    .appendSeconds()
                    .appendSeparator(".")
                    .appendMillis()
                    .toFormatter();
        }
        String ret = daysHoursMinutes.print(period.normalizedStandard());
        int pos = ret.indexOf(".");
        if ((pos != -1) && (pos > ret.length() - 4)) {
            int numberOfZeros = 4 - (ret.length() - pos);
            for (int i = 0; i < numberOfZeros; i++) {
                ret += "0";
            }
        }
        return ret;
        /**/
    }

    /**
     * @return the logListener
     */
    public LogListener getLogListener() {
        return logListener;
    }

    /**
     * @param logListener the logListener to set
     */
    public void setLogListener(LogListener logListener) {
        this.logListener = logListener;
    }
}
