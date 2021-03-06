/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused")

package net.mamoe.mirai.console

import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.MiraiConsoleImplementation.Companion.start
import net.mamoe.mirai.console.command.ConsoleCommandSender
import net.mamoe.mirai.console.data.PluginDataStorage
import net.mamoe.mirai.console.internal.MiraiConsoleImplementationBridge
import net.mamoe.mirai.console.plugin.PluginLoader
import net.mamoe.mirai.console.plugin.jvm.JarPluginLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalAPI
import net.mamoe.mirai.console.util.ConsoleInput
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.MiraiLogger
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.annotation.AnnotationTarget.*
import kotlin.coroutines.CoroutineContext


/**
 * 标记一个仅用于 [MiraiConsole] 前端实现的 API. 这些 API 只应由前端实现者使用, 而不应该被插件或其他调用者使用.
 */
@Retention(AnnotationRetention.SOURCE)
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(CLASS, TYPEALIAS, FUNCTION, PROPERTY, FIELD, CONSTRUCTOR)
@MustBeDocumented
public annotation class ConsoleFrontEndImplementation

/**
 * 由前端实现这个接口
 *
 * @see  MiraiConsoleImplementation.start
 */
@ConsoleFrontEndImplementation
public interface MiraiConsoleImplementation : CoroutineScope {
    /**
     * [MiraiConsole] 的 [CoroutineScope.coroutineContext]
     */
    public override val coroutineContext: CoroutineContext

    /**
     * Console 运行根目录
     * @see MiraiConsole.rootPath
     */
    public val rootPath: Path

    /**
     * Console 前端接口
     */
    @ConsoleExperimentalAPI
    public val frontEndDescription: MiraiConsoleFrontEndDescription

    /**
     * 与前端交互所使用的 Logger
     */
    public val mainLogger: MiraiLogger

    /**
     * 内建加载器列表, 一般需要包含 [JarPluginLoader].
     *
     * @return 不可变的 [List]
     */
    public val builtInPluginLoaders: List<PluginLoader<*, *>>

    public val consoleCommandSender: ConsoleCommandSender

    public val dataStorageForJarPluginLoader: PluginDataStorage
    public val configStorageForJarPluginLoader: PluginDataStorage
    public val dataStorageForBuiltIns: PluginDataStorage
    public val configStorageForBuiltIns: PluginDataStorage

    /**
     * @see ConsoleInput 的实现
     */
    public val consoleInput: ConsoleInput

    /**
     * 创建一个 [LoginSolver]
     *
     * **调用备注**: 此函数通常在构造 [Bot] 实例时调用.
     *
     * @param requesterBot 请求者 [Bot.id]
     * @param configuration 请求者 [Bot.configuration]
     *
     * @see LoginSolver.Default
     */
    public fun createLoginSolver(requesterBot: Long, configuration: BotConfiguration): LoginSolver

    /**
     * 创建一个 logger
     */
    public fun newLogger(identity: String?): MiraiLogger

    public companion object {
        internal lateinit var instance: MiraiConsoleImplementation
        private val initLock = ReentrantLock()

        /** 由前端调用, 初始化 [MiraiConsole] 实例, 并启动 */
        @JvmStatic
        @ConsoleFrontEndImplementation
        @Throws(MalformedMiraiConsoleImplementationError::class)
        public fun MiraiConsoleImplementation.start(): Unit = initLock.withLock {
            this@Companion.instance = this
            MiraiConsoleImplementationBridge.doStart()
        }
    }
}
