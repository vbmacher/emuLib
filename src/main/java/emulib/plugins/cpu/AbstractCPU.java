/*
 * KISS, YAGNI, DRY
 *
 * (c) Copyright 2010-2014, Peter Jakubčo
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package emulib.plugins.cpu;

import emulib.annotations.PluginType;
import emulib.emustudio.SettingsManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class implements some fundamental functionality that can be used
 * by your own plug-ins.
 *
 * The CPU execution is realized via separated thread.
 */
public abstract class AbstractCPU implements CPU, Runnable {
    /**
     * List of all CPU stateObservers
     */
    protected List<CPUListener> stateObservers;

    /**
     * breakpoints list
     */
    protected Set<Integer> breaks;

    /**
     * ID of this plug-in assigned by emuStudio.
     */
    protected long pluginID;

    /**
     * Run state of this CPU.
     */
    protected RunState runState;

    /**
     * Object for settings manipulation.
     */
    protected SettingsManager settings;

    /**
     * This thread object. It is used for the CPU execution.
     */
    protected Thread cpuThread;


    /**
     * Public constructor initializes run state and some variables.
     *
     * @param pluginID plug-in identification number
     */
    public AbstractCPU(Long pluginID) {
        runState = RunState.STATE_STOPPED_NORMAL;
        breaks = new HashSet<>();
        stateObservers = new ArrayList<>();
        this.pluginID = pluginID;
        cpuThread = null;
    }

    /**
     * This method initializes the CPU. It stores pluginID and settings into
     * variables.
     *
     * @param settings object for settings manipulation
     * @return true
     */
    @Override
    public boolean initialize(SettingsManager settings) {
        this.settings = settings;
        return true;
    }

    @Override
    public String getTitle() {
        return getClass().getAnnotation(PluginType.class).title();
    }

    /**
     * This class support breakpoints.
     *
     * @return true
     */
    @Override
    public boolean isBreakpointSupported() {
        return true;
    }

    @Override
    public void setBreakpoint(int memLocation) {
        breaks.add(memLocation);
    }

    @Override
    public void unsetBreakpoint(int memLocation) {
        breaks.remove(memLocation);
    }

    @Override
    public boolean isBreakpointSet(int memLocation) {
        return breaks.contains(memLocation);
    }

    /**
     * This method resets the CPU by calling overriden method reset(int).
     */
    @Override
    public void reset() { reset(0); }

    /**
     * Sets the run_state to STATE_STOPPED_BREAK and nulls the thread
     * object. Should be overriden.
     *
     * @param addr memory location where to begin the emulation
     */
    @Override
    public void reset(int addr) {
        runState = RunState.STATE_STOPPED_BREAK;
        cpuThread = null;
        notifyStateChanged(runState);
    }

    /**
     * Add new CPU listener to the list of stateObservers. CPU listener is an
     * implementation object of CPUListener interface. The methods are
     * called when some events are occured on CPU.
     *
     * @param listener CPUListener object
     * @return true if the listener was added, false otherwise
     */
    @Override
    public boolean addCPUListener(CPUListener listener) {
        return stateObservers.add(listener);
    }

    /**
     * Remove CPU listener object from the list of stateObservers. If the listener
     * is not included in the list, nothing will be done.
     *
     * @param listener CPUListener object
     * @return true if the listener was return, false otherwise
     */
    @Override
    public boolean removeCPUListener(CPUListener listener) {
        return stateObservers.remove(listener);
    }

    /**
     * Notifies all observers that CPU state has been changed.
     *
     * @param runState new CPU state
     */
    public void notifyStateChanged(RunState runState) {
        for (CPUListener observer : stateObservers) {
            observer.runStateChanged(runState);
            observer.internalStateChanged();
        }
    }

    /**
     * Creates and starts new thread of this class.
     */
    @Override
    public void execute() {
        cpuThread = new Thread(this);
        cpuThread.start();
    }

}
