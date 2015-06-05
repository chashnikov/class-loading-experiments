/**
 * @author nik
 */
package classLoaders

import java.io.File
import kotlin.util.measureTimeMillis
import com.intellij.util.lang.UrlClassLoader
import java.net.URLClassLoader

val useStandardClassloader = true

fun createClassLoader(files: List<File>, parent: ClassLoader): ClassLoader {
    val urls = files.map { it.toURI().toURL() }.copyToArray()
    if (useStandardClassloader) {
        return URLClassLoader(urls, parent)
    }
    else {
        return UrlClassLoader.build()!!.parent(parent)!!.urls(listOf(*urls))!!.allowLock()!!.useCache()!!.get()!!
    }
}

fun separateClassLoaders(jarsDir: File): List<Pair<String, ClassLoader>> {
    val platformFiles = jarsDir.listFiles { it.name.startsWith("util") || it.name.startsWith("platform") }!!
    val platformLoader = createClassLoader(listOf(*platformFiles), ClassLoader.getSystemClassLoader()!!)

    return jarsDir.listFiles { it.name.startsWith("plugin") }!!.map { Pair(it.name.trimTrailing(".jar"), createClassLoader(listOf(it), platformLoader)) }
}

fun commonClassLoader(jarsDir: File): List<Pair<String, ClassLoader>> {
    val allFiles = jarsDir.listFiles { it.name.endsWith(".jar") }!!
    val loader = createClassLoader(listOf(*allFiles), ClassLoader.getSystemClassLoader()!!)

    return jarsDir.listFiles { it.name.startsWith("plugin") }!!.map { Pair(it.name.trimTrailing(".jar"), loader) }
}

fun singleJarLoader(): List<Pair<String, ClassLoader>> {
    val loader = createClassLoader(listOf(File("all.jar")), ClassLoader.getSystemClassLoader()!!)

    return (1..50).map { Pair("plugin$it", loader) }
}

fun singleDirLoader(): List<Pair<String, ClassLoader>> {
    val loader = createClassLoader(listOf(File("gen/util-classes")), ClassLoader.getSystemClassLoader()!!)

    return listOf(Pair("util", loader))
}

fun main(args: Array<String>) {
    val jarsDir = File("jars")
    val plugins = singleDirLoader()
    val duration = measureTimeMillis {
        for ((moduleName, loader) in plugins) {
            val aClass = Class.forName("org.$moduleName.Entry", true, loader)
            aClass.newInstance()
        }
    }
    println(sun.misc.PerfCounter.getFindClasses())
    println(sun.misc.PerfCounter.getFindClassTime())
    println(sun.misc.PerfCounter.getParentDelegationTime())
    println(sun.misc.PerfCounter.getReadClassBytesTime())
    println("${plugins.size} plugins loaded in ${duration}ms")

}
/*
UrlClassLoader, separate jars, separate loaders:
sun.classloader.findClasses = 3929
sun.classloader.findClassTime = 829630482
sun.classloader.parentDelegationTime = 661464219
sun.urlClassLoader.readClassBytesTime = 15467969
50 plugins loaded in 1444ms

URLClassLoader, separate jars, separate loaders:
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 793675884
sun.classloader.parentDelegationTime = 661767856
sun.urlClassLoader.readClassBytesTime = 250237847
50 plugins loaded in 1491ms

UrlClassLoader, separate jars, single loader:
sun.classloader.findClasses = 3929
sun.classloader.findClassTime = 875458856
sun.classloader.parentDelegationTime = 591688374
sun.urlClassLoader.readClassBytesTime = 15442300
50 plugins loaded in 1417ms

URLClassLoader, separate jars, single loader
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 856273435
sun.classloader.parentDelegationTime = 568003825
sun.urlClassLoader.readClassBytesTime = 238703796
50 plugins loaded in 1442ms

UrlClassLoader, single jar:
sun.classloader.findClasses = 3922
sun.classloader.findClassTime = 837644812
sun.classloader.parentDelegationTime = 587112273
sun.urlClassLoader.readClassBytesTime = 15295192
50 plugins loaded in 1379ms

URLClassLoader, single jar
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 755237556
sun.classloader.parentDelegationTime = 581553481
sun.urlClassLoader.readClassBytesTime = 246043223
50 plugins loaded in 1362ms



*/

