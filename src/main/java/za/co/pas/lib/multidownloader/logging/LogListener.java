/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package za.co.pas.lib.multidownloader.logging;

/**
 *
 * @author andre
 */
public interface LogListener {
    void logInfo(String info, String... more);
    void logWarn(String info, String... more);
    void logError(String info, String... more);
}
