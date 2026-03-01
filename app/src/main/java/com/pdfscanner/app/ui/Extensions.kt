package com.pdfscanner.app.ui

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Show a Snackbar anchored to this fragment's view.
 * Uses fragment's root view — safe even if root is not CoordinatorLayout.
 * Guards against detached fragment (view == null).
 */
fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { Snackbar.make(it, message, duration).show() }
}

fun Fragment.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { Snackbar.make(it, messageRes, duration).show() }
}
