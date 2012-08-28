/*
 * ContextPool.java
 *
 * KISS, YAGNI, DRY
 * 
 * (c) Copyright 2010-2012, Peter Jakubčo
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

package emulib.runtime;

import emulib.emustudio.API;
import emulib.plugins.Context;
import emulib.plugins.compiler.CompilerContext;
import emulib.plugins.cpu.CPUContext;
import emulib.plugins.device.DeviceContext;
import emulib.plugins.memory.MemoryContext;
import emulib.runtime.interfaces.PluginConnections;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages all plug-in contexts.
 * 
 * Plug-ins should register their contexts manually. Other plug-ins that have permissions, can gather contexts by
 * querying this pool.
 *
 * @author vbmacher
 */
public class ContextPool {
    private final static Logger logger = LoggerFactory.getLogger(ContextPool.class);
    
    /**
     * The following map stores all registered contexts.
     * 
     * Contexts implementing the same context interfaces are stored to the end of the list under the same map key
     */
    private Map<String,List<Context>> allContexts;

    /**
     * This map represents owners of registered contexts (these are keys).
     * It is used for checking the plug-in permissions.
     */
    private Map<Long,List<Context>> contextOwners;

    // instance of this class
    private static ContextPool instance = new ContextPool();

    /**
     * Virtual computer loaded by emuStudio
     */
    private PluginConnections computer;
    
    /**
     * Private constructor.
     */
    private ContextPool() {
        allContexts = new HashMap<String, List<Context>>();
        contextOwners = new HashMap<Long, List<Context>>();
    }
    
    /**
     * Return an instance of this class. By calling more than 1 time, the same
     * instance is returned.
     *
     * @return ContextPool instance
     */
    public static ContextPool getInstance() {
        return instance;
    }
    
    /**
     * This method registers plug-in's context interface.
     * 
     * The registration is needed because of contexts centralization. Other plug-ins can get a context by
     * querying the pool. Usually, emuStudio does the job during loading of the virtual computer.
     *
     * Requirements for the context are:
     *   - It is allowed to implement one and only one context
     *   - context interface must extend Context interface provided by emuLib
     *   - context interface must be annotated with @ContextType annotation
     *
     * @param pluginID owner plugin ID of the context contextsByOwner
     * @param context The context object that the plug-in want to register
     * @param contextInterface The interface that the context has to implement
     * @throws AlreadyRegisteredException Raised when a plug-in tries to register context that is already registered.
     * @throws InvalidContextException Raised when a class does not implement given interface, or it is not
     *         annotated, or if the context interface does not fulfill context requirements.
     */
    public void register(long pluginID, Context context, Class<? extends Context> contextInterface)
            throws AlreadyRegisteredException, InvalidContextException {
        trustedContext(contextInterface);
        String contextHash = computeHash(contextInterface);

        // if the context is already registered, return false
        List contextsByHash = allContexts.get(contextHash);
        if ((contextsByHash != null) && contextsByHash.contains(context)) {
            throw new AlreadyRegisteredException();
        }
        // check if the contextInterface is implemented by the context
        if (!PluginLoader.doesImplement(context.getClass(), contextInterface)) {
            throw new InvalidContextException("Context does not implement context interface");
        }
        
        // finally register the context
        List<Context> contextsByOwner = contextOwners.get(pluginID);
        if (contextsByOwner == null) {
            contextsByOwner = new ArrayList<Context>();
            contextOwners.put(pluginID, contextsByOwner);
        }
        contextsByOwner.add(context);

        if (contextsByHash == null) {
            contextsByHash = new ArrayList<Context>();
            allContexts.put(contextHash, contextsByHash);
        }
        contextsByHash.add(context);
    }
    
    /**
     * Check if the provided class is a context.
     * 
     * @param contextInterface the context interface
     */
    private void trustedContext(Class<? extends Context> contextInterface) throws InvalidContextException {
        if (contextInterface == null) {
            throw new InvalidContextException("Interface is null");
        }
        if (!contextInterface.isInterface()) {
            throw new InvalidContextException("Given class is not interface");
        }
        if (!contextInterface.isAnnotationPresent(emulib.annotations.ContextType.class)) {
            throw new InvalidContextException("Interface is not annotated as context");
        }
    }

    /**
     * Unregisters all contexts of given context interface.
     * 
     * It will do it only if the plug-in has the permission. The permission is approved if and only if the contexts are
     * implemented inside the plug-in.
     * 
     * @param pluginID plugin ID of the context owner
     * @param contextInterface the context interface
     * @return true if at least one context was unregistered successfully, false otherwise.
     * @throws InvalidContextException Raised when context interface is not annotated, or if the context interface does
     *         not fulfill context requirements.
     * 
     */
    public boolean unregister(long pluginID, Class<? extends Context> contextInterface) throws InvalidContextException {
        trustedContext(contextInterface);
        
        List<Context> contextsByOwner = contextOwners.get(pluginID);
        if (contextsByOwner == null) {
            return false;
        }

        String contextHash = computeHash(contextInterface);
        List<Context> contextsByHash = allContexts.get(contextHash);

        if (contextsByHash == null) {
            return false;
        }

        boolean result = true;
        Iterator<Context> contextIterator = contextsByHash.iterator();
        while (contextIterator.hasNext()) {
            Context context = contextIterator.next();
            if (contextsByOwner.contains(context)) {
                result = result && contextsByOwner.remove(context);
                contextIterator.remove();
            }
        }
        if (contextsByHash.isEmpty()) {
            allContexts.remove(contextHash);
        }
        return result;
    }

    /**
     * Set a computer, represented as plug-in connections, loaded by emuStudio.
     * 
     * This method should be called only by the emuStudio.
     * 
     * @param password emuStudio password
     * @param computer virtual computer, loaded by emuStudio
     * @return true if computer was set successfully; false otherwise.
     * @throws InvalidPasswordException if the password was incorrect
     */
    public boolean setComputer(String password, PluginConnections computer) throws InvalidPasswordException {
        API.testPassword(password);
        this.computer = computer;
        return true;
    }

    /**
     * Get plug-in context.
     *
     * @param pluginID ID of requesting plug-in
     * @param contextInterface wanted context interface (implemented by the plug-in)
     * @param index the index if more than one context are found
     * @return requested context; null if the context does not exist or the plug-in is not allowed to get it
     */
    public Context getContext(long pluginID, Class<? extends Context> contextInterface, int index) {
        trustedContext(contextInterface);
        // find the requested context
        List<Context> contextsByHash = allContexts.get(computeHash(contextInterface));
        if ((contextsByHash == null) || contextsByHash.isEmpty()) {
            return null;
        }
        
        // find context based on contextID
        int j = 0;
        for (Context context : contextsByHash) {
            if (checkPermission(pluginID, context)) {
                if (j == index) {
                    return context;
                }
            }
            j++;
        }
        return null;
    }
    
    /**
     * Get registered CPU context.
     * 
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the CPU in the abstract schema.
     *
     * If the CPU has more than one context implementing required context interface, the first one is returned. To
     * access other ones, use extended version of the method.
     *
     * @param pluginID plug-in requesting the CPU context
     * @param contextInterface Interface of the context
     * @return CPUContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public CPUContext getCPUContext(long pluginID, Class<? extends CPUContext> contextInterface) {
        return (CPUContext)getContext(pluginID, contextInterface, 0);
    }

    /**
     * Get registered CPU context (extended version).
     * 
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the CPU in the abstract schema.
     *
     * If the CPU has more than one context implementing required context interface, it returns context indexed by index
     * parameter.
     *
     * @param pluginID plug-in requesting the CPU context
     * @param contextInterface Interface of the context
     * @param index 0-based the order of the context if they are more than one. Does nothing if the index is out of
     *        the bounds.
     * @return CPUContext object if it is found and the plug-in has the permission; null otherwise
     */
    public CPUContext getCPUContext(long pluginID, Class<? extends CPUContext> contextInterface, int index) {
        return (CPUContext)getContext(pluginID, contextInterface, index);
    }

    /**
     * Get registered compiler context.
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the compiler in the abstract schema.
     *
     * If the compiler has more than one context implementing required context interface, the first one is returned. To
     * access other ones, use extended version of the method.
     *
     * @param pluginID plug-in requesting the compiler context
     * @param contextInterface Interface of the context, if requesting plugin has permission to acccess it
     * @return CompilerContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public CompilerContext getCompilerContext(long pluginID, Class<? extends CompilerContext> contextInterface) {
        return (CompilerContext)getContext(pluginID, contextInterface, 0);
    }

    /**
     * Get registered compiler context (extended version).
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the compiler in the abstract schema.
     *
     * If the compiler has more than one context implementing required context interface, it returns context indexed by
     * index parameter.
     *
     * @param pluginID plug-in requesting the Compiler context
     * @param contextInterface Interface of the context
     * @param index the order of the context if they are more than one. Does nothing if the index is out of bounds.
     * @return CompilerContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public CompilerContext getCompilerContext(long pluginID, Class<? extends CompilerContext> contextInterface, int index) {
        return (CompilerContext)getContext(pluginID, contextInterface, index);
    }

    /**
     * Get registered memory context.
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the memory in the abstract schema.
     *
     * If the memory has more than one context implementing required context interface, the first one is returned. To
     * access other ones, use extended version of the method.
     *
     * @param pluginID plug-in requesting the memory context
     * @param contextInterface Interface of the context
     * @return MemoryContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public MemoryContext getMemoryContext(long pluginID, Class<? extends MemoryContext> contextInterface) {
        return (MemoryContext)getContext(pluginID, contextInterface, 0);
    }

    /**
     * Get registered memory context (extended version).
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the memory in the abstract schema.
     *
     * If the memory has more than one context implementing required context interface, it returns context indexed by
     * index parameter.
     *
     * @param pluginID plug-in requesting the memory context
     * @param contextInterface Interface of the context
     * @param index the index of the context if they are more than one. Does nothing if the index is out of bounds
     * @return MemoryContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public MemoryContext getMemoryContext(long pluginID, Class<? extends MemoryContext> contextInterface, int index) {
        return (MemoryContext)getContext(pluginID, contextInterface, index);
    }

    /**
     * Get registered device context.
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the device in the abstract schema.
     *
     * If the device has more than one context implementing required context interface, the first one is returned. To
     * access other ones, use extended version of the method.
     *
     * @param pluginID plug-in requesting the device context
     * @param contextInterface Interface of the context
     * @return DeviceContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public DeviceContext getDeviceContext(long pluginID, Class<? extends DeviceContext> contextInterface) {
        return (DeviceContext)getContext(pluginID, contextInterface, 0);
    }

    /**
     * Get registered device context (extended version).
     *
     * If plug-in doesn't have the permission to access it, return null. The permission is approved, when the
     * plug-in is connected to the device in the abstract schema.
     *
     * If the device has more than one context implementing required context interface, it returns context indexed by
     * index parameter.
     *
     * @param pluginID plug-in requesting the device context
     * @param contextInterface Interface of the context
     * @param index index of the context implementation. Does nothing if the index is out of bounds.
     * @return DeviceContext object if it is found and the plug-in has the permission to access it; null otherwise
     */
    public DeviceContext getDeviceContext(long pluginID, Class<? extends DeviceContext> contextInterface, int index) {
        return (DeviceContext)getContext(pluginID, contextInterface, index);
    }

    /**
     * This method check if the plug-in has the permission to access specified context.
     *
     * The permission is granted if and only if the context is connected to the plug-in inside virtual computer.
     *
     * @param pluginID plug-in to check
     * @param context requested context
     * @return true if the plug-in is approved to access the context; false otherwise
     */
    private boolean checkPermission(long pluginID, Context context) {
        // check if it is possible to check the plug-in for the permission
        if (computer == null) {
            return false;
        }
        // first it must be found the contextsByOwner of the ContextPool.
        Long contextOwner = null;
        for (long pID : contextOwners.keySet()) {
            List<Context> contextsByOwner = contextOwners.get(pID);
            if (contextsByOwner == null) {
                continue;
            }
            if (contextsByOwner.contains(context)) {
                contextOwner = pID;
                break;
            }
        }
        if (contextOwner == null) {
            return false;
        }
        // THIS is the permission check
        return computer.isConnected(pluginID, contextOwner);
    }

    /**
     * Compute emuStudio-specific hash of the context interface.
     * 
     * The final processing uses SHA-1 method.
     *
     * @param contextInterface interface to compute hash of
     * @return SHA-1 hash string of the interface
     */
    public static String computeHash(Class<? extends Context> contextInterface) {
        List<Method> contextMethods = Arrays.asList(contextInterface.getDeclaredMethods());
        Collections.<Method>sort(contextMethods, new Comparator<Method>() {

            @Override
            public int compare(Method m1, Method m2) {
                return m1.getName().compareTo(m2.getName());
            }

        });

        String hash = "";
        for (Method method : contextMethods.toArray(new Method[0])) {
            hash += method.getGenericReturnType().toString() + " " + method.getName() + "(";
            for (Class<?> param : method.getParameterTypes()) {
                hash += param.getName() + ",";
            }
            hash += ");";
        }
        try {
            return SHA1(hash);
        } catch(Exception e) {
            logger.error("Could not compute hash.", e);
            return null;
        }
    }

    /**
     * Compute SHA-1 hash string.
     * 
     * Letters in the hash string will be in upper-case.
     *
     * @param text Data to make hash from
     * @return SHA-1 hash Hexadecimal string, null if there was some error
     */
    public static String SHA1(String text) {
        try {
            MessageDigest md;
            md = MessageDigest.getInstance("SHA-1");
            byte[] sha1hash;
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            sha1hash = md.digest();
            return RadixUtils.convertToRadix(sha1hash, 16, false);
        } catch (NoSuchAlgorithmException e) {
        } catch (UnsupportedEncodingException r) {
        }
        return null;
    }
}