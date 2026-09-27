package com.viva.downloader.ui.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private const val FORUM_URL = "https://bbs.viva-la-vita.org/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var showWebView by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // WebView 模式时轮询登录态
    DisposableEffect(showWebView) {
        if (showWebView) {
            viewModel.startPolling()
        } else {
            viewModel.stopPolling()
        }
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.loggedIn) "已登录" else "登录论坛",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4D698E),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.loggedIn) {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "退出登录")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF6F1E5)),
            )
        },
        containerColor = Color(0xFFF6F1E5),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loggedIn) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✅ 已成功登录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("现在可以浏览论坛并下载视频了", color = Color(0xFF5C5344))
                    Button(onClick = onBack) {
                        Text("开始使用")
                    }
                }
            } else if (showWebView) {
                // WebView 备用登录方式
                WebViewLogin()
                Text(
                    "网页登录中，请完成登录后自动跳转…",
                    color = Color(0xFF5C5344),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            } else {
                // 原生登录表单
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(20.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "账号密码登录",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF262019),
                            )

                            OutlinedTextField(
                                value = state.identification,
                                onValueChange = viewModel::onIdentificationChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("用户名或邮箱") },
                                singleLine = true,
                                enabled = !state.submitting,
                            )

                            OutlinedTextField(
                                value = state.password,
                                onValueChange = viewModel::onPasswordChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("密码") },
                                singleLine = true,
                                enabled = !state.submitting,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                        )
                                    }
                                },
                            )

                            state.error?.let { err ->
                                Text(err, color = Color(0xFFCC2E0C), fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.login() },
                                enabled = !state.submitting,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4D698E)),
                            ) {
                                if (state.submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("登录中…", color = Color.White)
                                } else {
                                    Text("登录", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { showWebView = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("使用网页登录", color = Color(0xFF4D698E))
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLogin() {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {}
                loadUrl(FORUM_URL)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
