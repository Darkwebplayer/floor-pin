package dev.infyplus.floorpin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

data class FieldError(val field: String, val message: String)

@Stable
class Validator {
    var errors by mutableStateOf(emptySet<FieldError>())
        private set

    fun errorFor(field: String): String? = errors.firstOrNull { it.field == field }?.message

    fun hasError(field: String): Boolean = errors.any { it.field == field }

    fun validate(vararg rules: Pair<String, String?>) {
        errors = rules.mapNotNull { (field, message) -> if (message != null) FieldError(field, message) else null }.toSet()
    }

    fun clearField(field: String) {
        errors = errors.filterNot { it.field == field }.toSet()
    }

    fun clearAll() {
        errors = emptySet()
    }

    val valid: Boolean get() = errors.isEmpty()
}

private val EMAIL_RE = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

fun validateRequired(value: String, label: String): String? =
    if (value.isBlank()) "$label is required" else null

fun validateEmail(value: String): String? = when {
    value.isBlank() -> "Email address is required"
    !value.matches(EMAIL_RE) -> "Enter a valid email address"
    else -> null
}

fun validateRange(value: Double, label: String, min: Double = 0.0, max: Double = 100.0): String? =
    if (value < min || value > max) "$label must be between $min and $max" else null

fun validateFileRequired(bytes: ByteArray?): String? =
    if (bytes == null) "Please select a file" else null

@Composable
fun rememberValidator(): Validator = remember { Validator() }
