package io.github.nodyssey.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.awt.Component

/** Adopts the sandboxed Chromium view into the application's heavyweight Swing host. */
internal object EmbeddedVerification {
    interface User32 : StdCallLibrary {
        fun SetParent(child: Pointer, parent: Pointer): Pointer?
        fun GetParent(child: Pointer): Pointer?
        fun IsWindow(window: Pointer): Boolean
        fun GetWindowLongW(window: Pointer, index: Int): Int
        fun SetWindowLongW(window: Pointer, index: Int, value: Int): Int
        fun GetClientRect(window: Pointer, rect: Rect): Boolean
        fun MoveWindow(window: Pointer, x: Int, y: Int, width: Int, height: Int, repaint: Boolean): Boolean
    }

    @Structure.FieldOrder("left", "top", "right", "bottom")
    class Rect : Structure() {
        @JvmField var left = 0

        @JvmField var top = 0

        @JvmField var right = 0

        @JvmField var bottom = 0
    }
    private val user32 = Native.load("user32", User32::class.java)
    fun resize(handle: String, host: Component, attach: Boolean) {
        val child = Pointer(handle.toLong())
        if (!user32.IsWindow(child)) return
        val parent = Native.getComponentPointer(host)
        if (attach) {
            user32.SetWindowLongW(child, -16, (user32.GetWindowLongW(child, -16) and 0x80000000.toInt().inv()) or 0x40000000)
            user32.SetParent(child, parent)
            check(user32.GetParent(child) == parent) { "Unable to embed verification view (${Native.getLastError()})" }
        }
        check(user32.GetParent(child) == parent) { "Verification view detached from application" }
        val rect = Rect()
        check(user32.GetClientRect(parent, rect))
        val current = Rect()
        if (attach || !user32.GetClientRect(child, current) || current.right != rect.right || current.bottom != rect.bottom) user32.MoveWindow(child, 0, 0, rect.right, rect.bottom, true)
    }
}
