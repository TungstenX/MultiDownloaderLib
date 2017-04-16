/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package za.co.pas.lib.multidownloader.logging;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 *
 * @author Andre Labuschagne
 */
public class MDLogFormatter extends Formatter {
    public static final String NO_PRINTLINE = "[#NP#]";

    @Override
    public String format(LogRecord record) {
        
        StringBuilder sb = new StringBuilder();
        if(record.getMessage().startsWith(NO_PRINTLINE)) {
            sb.append(record.getMessage().substring(NO_PRINTLINE.length()));
        } else {
            sb.append(record.getLevel()).append(":\t").append(record.getMessage()).append("\n");        
            if(record.getThrown() != null) {
                sb.append("\t\t").append(record.getThrown().getMessage()).append("\n");
            }
        }
        
        if(record.getParameters() != null) {
            int i = 0;
            for(Object o : record.getParameters()) {
                String needle = "{" + i + "}";
                int pos = sb.indexOf(needle);
                if(pos != -1) {
                    sb.delete(pos, pos + needle.length());
                    sb.insert(pos, o.toString());
                }
                i++;
            }
        }
        return  sb.toString();
    }
}
