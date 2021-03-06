/*
 * This file is part of emuLib.
 *
 * Copyright (C) 2006-2020  Peter Jakubčo
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.emustudio.emulib.plugins.cpu;

import net.emustudio.emulib.plugins.annotations.PluginContext;
import net.emustudio.emulib.plugins.Context;
import net.emustudio.emulib.plugins.device.DeviceContext;

/**
 * CPU context can be used by plugins which are connected to CPU.
 *
 * Custom CPU contexts can extend the runtime functionality accessible to plugins. Plugins which need the specific
 * CPU contexts, should declare a dependency on the CPU plugin.
 */
@SuppressWarnings("unused")
@PluginContext
public interface CPUContext extends Context {

    /**
     * Determine whether this CPU supports raw interrupts.
     *
     * @return true, if raw interrupts are supported, false otherwise
     */
    boolean isRawInterruptSupported();

    /**
     * Send raw interrupt signal to the CPU.
     *
     * Does nothing if raw interrupts are not supported.
     *
     * @param device Device that interrupts the CPU
     * @param data interrupt data
     */
    void signalRawInterrupt(DeviceContext<?> device, byte []data);

    /**
     * Determine whether this CPU supports interrupts.
     *
     * @return true, if interrupts are supported, false otherwise
     */
    boolean isInterruptSupported();

    /**
     * Send interrupt signal to the CPU.
     *
     * @param device Device that interrupts the CPU
     * @param mask interrupt mask
     */
    void signalInterrupt(DeviceContext<?> device, int mask);

    /**
     * Cancel sending interrupt signal if it was not handled yet.
     *
     * @param device Device that wants to cancel the interrupt signal
     * @param mask clear interrupt mask
     */
    void clearInterrupt(DeviceContext<?> device, int mask);

    /**
     * Get CPU frequency in kHz
     *
     * @return CPU frequency in kHz or 0 if it is not supported
     */
    int getCPUFrequency();

}

