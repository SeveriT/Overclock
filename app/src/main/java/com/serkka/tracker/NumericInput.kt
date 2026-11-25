@file:OptIn(ExperimentalMaterial3Api::class)

package com.serkka.tracker

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow

/**
 * Number entry with −/+ steppers: the M3 filled text field (floating [label], indicator
 * line, accent focus colours — see [TrackerTextField]) with − as the leading icon and + as
 * the trailing one. [suffix] shows a unit after the number (e.g. "kg").
 */
@Composable
fun NumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    imeAction: ImeAction = ImeAction.Default,
    onNext: (() -> Unit)? = null,
    // Decimal typing is independent of the −/+ step (e.g. weight: ±5 buttons, but 62.5 typed).
    allowDecimal: Boolean = step % 1 != 0f,
    suffix: String? = null
) {
    val isInteger = !allowDecimal

    TextField(
        value = value,
        onValueChange = { newValue: String ->
            if (!isInteger || (!newValue.contains(".") && !newValue.contains(","))) {
                onValueChange(newValue)
            }
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = suffix?.let { { Text(it) } },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onNext?.invoke() }
        ),
        singleLine = true,
        colors = trackerTextFieldColors(),
        leadingIcon = {
            IconButton(
                onClick = {
                    val current = value.toLeadFloat() ?: 0f
                    if (current > 0f) {
                        val next = if (isInteger) (current.toInt() - step.toInt()).toFloat()
                                   else current - step
                        onValueChange(formatWeight(next.coerceAtLeast(0f)))
                    }
                }
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    val current = value.toLeadFloat() ?: 0f
                    val next = if (isInteger) (current.toInt() + step.toInt()).toFloat()
                               else current + step
                    onValueChange(formatWeight(next))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    )
}
