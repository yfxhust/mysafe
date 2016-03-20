# MySafe
My Unsafe

1. What is MySafe?
==============

**MySafe** is a framework (based on [Jillegal-Agent](https://github.com/serkan-ozal/jillegal-agent)) for managing memory accesses over `sun.misc.Unsafe`. **MySafe** intercepts (instruments) `sun.misc.Unsafe` calls and keeps records of allocated memories. So it can give the allocated memory informations and detect the invalid memory accesses.

2. Installation
==============

In your `pom.xml`, you must add repository and dependency for **MySafe**. 
You can change `mysafe.version` to any existing **MySafe** library version.
Latest version of **MySafe** is `2.0-SNAPSHOT`.

``` xml
...
<properties>
    ...
    <mysafe.version>2.0-SNAPSHOT</mysafe.version>
    ...
</properties>
...
<dependencies>
    ...
	<dependency>
		<groupId>tr.com.serkanozal</groupId>
		<artifactId>mysafe</artifactId>
		<version>${mysafe.version}</version>
	</dependency>
	...
</dependencies>
...
<repositories>
	...
	<repository>
		<id>serkanozal-maven-repository</id>
		<url>https://github.com/serkan-ozal/maven-repository/raw/master/</url>
	</repository>
	...
</repositories>
...
```

3. Configurations
==============
* **`mysafe.enableSafeMemoryManagementMode`:** Enables checkes while freeing/reallocating memory. By this property enabled, every memory free/reallocation are checked about if the target memory is valid (already allocated) or not. Default value is `false`.

* **`mysafe.enableSafeMemoryAccessMode`:** Enables memory access checkes over `sun.misc.Unsafe`. By this property enabled, every memory accesses over `sun.misc.Unsafe` are checked about if the target memory is valid (already allocated) or not. Default value is `false`.
 
* **`mysafe.enableConcurrentMemoryAccessCheck`:** Enables a very lightweight locking for every memory access/free operation. By this property enabled, when there is on going memory access, there cannot be memory free and when there is on going memory free, there cannot be memory access. However, when there is on going memory access, there can be other memory accesses and when there is on going memory free, there can be other memory frees. It means, memory accesses only lock memory frees and memory frees only lock memory accesses. This property can be used if there is no guarantee for that the accessed memory region can be free by other threads simultaneously. Note that this lock is not address based lock but global lock for memory access/free operation but it is very light weight and implemented by lock-free approaches with busy-spin based on the assumption that memory accesses/frees are very fast operations.

* **`mysafe.useCustomMemoryManagement`:** Enabled custom memory management mode. Custom memory management means that memory allocation/free/reallocation operations are not handled directly over `sun.misc.Unsafe` but over custom implementation. For example, user might acquire memory in batch from OS, caches it and then serves requested memories from there. In this mode, user can specify his/her custom memory allocation/free/reallocation points instead of `Unsafe::allocateMemory`/`Unsafe::freeMemory`/`Unsafe::reallocateMemory`. However, when this mode is enabled, **Safe Memory Access Mode** feature cannot be enabled at the same time. Custom memory management points can be configured via annotations (`@AllocationPoint`, `@FreePoint` and `@ReallocationPoint`) and properties file named `mysafe-config.properties`.

**TODO:** Explain how to specify custom memory management points with annotation or by properties file in detail.

* **`mysafe.customMemoryManagementPackagePrefix`:** Specifies a subset of classes/packages for checking loaded classes whether they might have custom memory management point. By this configuration, unnecessary check on every loaded classes is prevented for possible custom memory management points.

* **`mysafe.threadLocalMemoryUsagePatternExist`:** ???

* **`mysafe.threadLocalMemoryUsageDeciderImpl`:** ???

* **`mysafe.enableCallerInfoMonitoringMode`:** Enables tracking caller informations on memory allocation (class name, method name and line number) with at most `4` depth by default. Caller informations are dumped while dumping all allocated memories through `MySafe::dumpAllocatedMemories` if it is enabled. Default value is `false`.

* **`mysafe.maxCallerInfoDepth`:** Configures maximum depth of for caller information tracking. Default value is `4`.

* **`mysafe.enableMXBean`:** Enables JMX support. Default value is `false`.

* **`mysafe.allocatedMemoryStorageImpl`:** Specifies the custom `AllocatedMemoryStorage` implementation which stored the allocated memories. If it is not set, the default (built-in) `AllocatedMemoryStorage` implementation is used.

* **`mysafe.illegalMemoryAccessListenerImpl`:** Specifies the `IllegalMemoryAccessListener` implementation to be notified when illegal memory access occurred.

* **`mysafe.useNativeMemoryForStorageWhenSupported`:** Enables usage of native memory (off-heap) backed storages when supported (only supported by thread-local storage at the moment).

4. Usage
==============

There are 3 ways of activating **MySafe**:

4.1. Java Agent Based Usage by VM Argument 
--------------
**MySafe** can be activated through Java agent (**Jillegal-Agent**) by using `sun.misc.Unsafe` instrumenter of **MySafe** via `-javaagent:<path_to_jillegal_agent>\<jillegal_agent_jar>="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`.

For example: `-javaagent:$M2_HOME\tr\com\serkanozal\jillegal-agent\2.0\jillegal-agent-2.0.jar="-p tr.com.serkanozal.mysafe.impl.processor.MySafeProcessor"`

4.2. Java Agent Based Usage Programmatically
--------------
**MySafe** can be activated programmatically by `MySafe.youAreMine();`.

4.3. ClassLoader Based Usage by VM Argument 
--------------
**MySafe** can be activated also by defining its classloader as system classloader via  `-Djava.system.class.loader=tr.com.serkanozal.mysafe.impl.classloader.MySafeClassLoader`.

5. API
==============

5.1. AllocatedMemoryStorage 
--------------
`AllocatedMemoryStorage` interface is contract point to store allocated memories. It is specified via `mysafe.allocatedMemoryStorageImpl` system property.

5.2. IllegalMemoryAccessListener 
--------------
`IllegalMemoryAccessListener` interface is contract point to be notified when illegal memory access occurred. It is specified via `mysafe.illegalMemoryAccessListenerImpl` system property. 

5.3. AllocatedMemoryIterator 
--------------
`AllocatedMemoryIterator` interface is contract point for iterating allocated memories.

Here is its sample usage:
``` java
// Iterate on all allocated memories and print them
MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {

  @Override
  public void onAllocatedMemory(long address, long size) {
    System.out.println("onAllocatedMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size);
  }
                
});
```

5.4. MemoryListener 
--------------
`MemoryListener` interface is contract point to be notified for memory usage (allocation/free/reallocation).

Here is its sample usage:
``` java
// Create listener to be notified for each allocate/free/reallocate
MemoryListener listener = new MemoryListener() {

  @Override
  public void beforeAllocateMemory(long size) {
    System.out.println("beforeAllocateMemory >>> " + 
                          "size=" + size);
  }
                
  @Override
  public void afterAllocateMemory(long address, long size) {
    System.out.println("afterAllocateMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size);
  }
                
  @Override
  public void beforeFreeMemory(long address) {
    System.out.println("beforeFreeMemory >>> " + 
                          "address=" + address);
  }
                
  @Override
  public void afterFreeMemory(long address, long size, boolean isKnownAddress) {
    System.out.println("afterFreeMemory >>> " + 
                          "address=" + address + 
                          ", size=" + size + 
                          ", isKnownAddress=" + isKnownAddress);
  }

  @Override
  public void beforeReallocateMemory(long oldAddress, long oldSize) {
    System.out.println("beforeReallocateMemory >>> " + 
                          "oldAddress=" + oldAddress + 
                          ", oldSize=" + oldSize);
  }

  @Override
  public void afterReallocateMemory(long oldAddress, long oldSize, 
                                    long newAddress, long newSize, boolean isKnownAddress) {
    System.out.println("afterReallocateMemory >>> " + 
                          "oldAddress=" + oldAddress + 
                          ", oldSize=" + oldSize +
                          ", newAddress=" + newAddress + 
                          ", newSize=" + newSize +
                          ", isKnownAddress=" + isKnownAddress);
  }
  
};

...

// Register listener to be notified for each allocate/free
MySafe.registerMemoryListener(listener);

...

// Deregister registered listener
MySafe.deregisterMemoryListener(listener);
```

5.5. Dumping Allocated Native Memories 
--------------
All allocated memories can be dumped via `MySafe.dumpAllocatedMemories()` or `MySafe.dumpAllocatedMemories(PrintStream)` methods.

Here is its sample usage:
``` java
// Dump all allocated memories to console
MySafe.dumpAllocatedMemories();

...

PrintStream myPrintStream = ...
// Dump all allocated memories to `myPrintStream`
MySafe.dumpAllocatedMemories(myPrintStream);
```

6. Demo
==============
[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/Demo.java) is its demo application.

[Here](https://github.com/serkan-ozal/mysafe/blob/master/src/test/java/tr/com/serkanozal/mysafe/CustomMemoryManagementDemo.java) is its demo application for demonstrating custom memory management support.

7. Fixes & Enhancements
==============
Bug fixes and enhancements at each release:

7.1. Version 1.1
--------------
* Introduced `UnsafeMemoryAccessor` for memory access abstraction through `Unsafe`. Also introduced `AlignmentAwareUnsafeMemoryAccessor` to support unaligned memory accesses on platforms which don't support unaligned memory accesses such as **SPARC**.

7.2. Version 2.0
--------------
* Some renaming on interfaces, classes and method names about **Unsafe** terms including API.
* Ability to specify custom memory allocation, reallocation and free points (methods) instead of `Unsafe`'s `allocateMemory`, `freeMemory` and `reallocateMemory` methods.
* Ability to monitor stacktraces of memory allocations by **class name**, **method name** (or **constructor**/**class initializer**) and **line number**.

8. Roadmap
==============
* Ability to storing allocated memory addresses and caller informations (if enabled) at off-heap instead of heap.
