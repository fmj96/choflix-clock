package com.choflix.clock.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** 숫자 비밀번호 길이. */
const val PIN_MIN = 4
const val PIN_MAX = 8

/**
 * 숫자 비밀번호 입력 창. [onSubmit] 이 오류 문구를 돌려주면 창을 그대로 두고 보여 주고,
 * null 이면 맞은 것으로 보고 창은 부른 쪽이 닫는다.
 */
@Composable
fun PinDialog(title: String, hint: String = "", onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        error = onSubmit(pin) ?: ""
        if (error.isNotEmpty()) pin = ""
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    if (hint.isNotEmpty()) Text(hint, color = Color.White.copy(alpha = 0.7f))
                    OutlinedTextField(
                        pin, { v -> if (v.length <= PIN_MAX && v.all(Char::isDigit)) pin = v },
                        label = { Text("숫자 $PIN_MIN~${PIN_MAX}자리") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = error.isNotEmpty(),
                        supportingText = { if (error.isNotEmpty()) Text(error) },
                        modifier = Modifier.focusRequester(focus),
                    )
                }
            },
            confirmButton = { TextButton(onClick = ::submit, enabled = pin.length >= PIN_MIN) { Text("확인") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        )
    }
}
