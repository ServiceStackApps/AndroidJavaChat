package servicestack.net.androidchat;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.gson.JsonObject;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterLoginButton;

import net.servicestack.client.JsonUtils;
import net.servicestack.client.Log;

import java.io.IOException;

import io.fabric.sdk.android.Fabric;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * This Login Page signs in using Facebook and Twitter's SDK Buttons
 */

public class LoginButtonsActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private TextView txtStatus;
    private ObjectAnimator animation;

    private TwitterLoginButton btnTwitterLogin;
    private LoginButton btnFacebookLogin;
    private CallbackManager facebookCallback;
    private Button btnGuestLogin;
    GoogleApiClient googleApiClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fabric.with(this, new Twitter(new TwitterAuthConfig(
            getString(R.string.twitter_key),
            getString(R.string.twitter_secret))));

        setContentView(R.layout.login_buttons);

        Toolbar toolbar = (Toolbar) findViewById(R.id.loginToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        txtStatus = (TextView)findViewById(R.id.txtStatus);
        UiHelpers.setStatus(txtStatus, "");

        animation = ObjectAnimator.ofInt(progressBar, "progress", 0, 500); // see this max value coming back here, we animale towards that value
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.setRepeatCount(100);
        animation.setDuration(1500);
        animation.setInterpolator(new FastOutLinearInInterpolator());

        LoginButtonsActivity activity = this;

        btnTwitterLogin = (TwitterLoginButton) findViewById(R.id.btnTwitterLogin);
        btnTwitterLogin.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                startProgressBar();
                UiHelpers.setStatus(txtStatus, "Local twitter sign-in successful, signing into server...");

                TwitterSession session = result.data;
                App.get().getServiceClient().postAsync(new dtos.Authenticate()
                    .setProvider("twitter")
                    .setAccessToken(session.getAuthToken().token)
                    .setAccessTokenSecret(session.getAuthToken().secret)
                    .setRememberMe(true),
                        r -> {
                            UiHelpers.setStatus(txtStatus, "Server twitter sign-in successful, opening chat...");
                            App.get().saveTwitterAccessToken(session.getAuthToken());
                            Intent intent = new Intent(activity, MainActivity.class);
                            stopProgressBar();
                            startActivity(intent);
                        },
                        error -> {
                            UiHelpers.setStatusError(txtStatus, "Server twitter sign-in failed", error);
                            stopProgressBar();
                        });
            }

            @Override
            public void failure(TwitterException exception) {
                Log.e(exception);
                stopProgressBar();
            }
        });

        facebookCallback = CallbackManager.Factory.create();
        btnFacebookLogin = (LoginButton) findViewById(R.id.btnFacebookLogin);
        btnFacebookLogin.setReadPermissions("email");
        btnFacebookLogin.registerCallback(facebookCallback, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                UiHelpers.setStatus(txtStatus, "Local facebook sign-in successful, signing into server...");

                App.get().getServiceClient().postAsync(new dtos.Authenticate()
                    .setProvider("facebook")
                    .setAccessToken(loginResult.getAccessToken().getToken())
                    .setRememberMe(true),
                    r -> {
                        UiHelpers.setStatus(txtStatus, "Server facebook sign-in successful, opening chat...");
                        Intent intent = new Intent(activity, MainActivity.class);
                        stopProgressBar();
                        startActivity(intent);
                    },
                    error -> {
                        UiHelpers.setStatusError(txtStatus, "Server facebook sign-in failed", error);
                        stopProgressBar();
                    });
            }

            @Override
            public void onCancel() {
                stopProgressBar();
            }

            @Override
            public void onError(FacebookException exception) {
                Log.e(exception);
                stopProgressBar();
            }
        });

        SignInButton btnGoogleSignIn = (SignInButton) findViewById(R.id.sign_in_button);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestServerAuthCode(getResources().getString(R.string.google_key))
            .build();
        googleApiClient = new GoogleApiClient.Builder(this)
            .enableAutoManage(this, r -> { /* Handle On Connection Failed...*/ })
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build();
        btnGoogleSignIn.setOnClickListener(v -> {
            Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient);
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        Button btnGuestLogin = (Button)findViewById(R.id.btnGuestLogin);
        btnGuestLogin.setOnClickListener(view -> {
            UiHelpers.setStatus(txtStatus, "Opening chat as guest...");
            App.get().getServiceClient().clearCookies();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        dtos.Authenticate authDto = App.get().getSavedAccessToken();
        if (authDto != null){
            UiHelpers.setStatus(txtStatus, "Signing in with saved " + authDto.getProvider() + " AccessToken...");
            App.get().getServiceClient().postAsync(authDto,
                r -> {
                    Intent intent = new Intent(activity, MainActivity.class);
                    stopProgressBar();
                    startActivity(intent);
                },
                error -> {
                    UiHelpers.setStatusError(txtStatus, "Error logging into " + authDto.getProvider() + " using Saved AccessToken", error);
                    stopProgressBar();
                });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        btnTwitterLogin.onActivityResult(requestCode, resultCode, data);
        facebookCallback.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            handleGoogleSignInResult(Auth.GoogleSignInApi.getSignInResultFromIntent(data));
        }
    }

    private void handleGoogleSignInResult(GoogleSignInResult result) {
        if (result.isSuccess()) {
            GoogleSignInAccount acct = result.getSignInAccount();
            UiHelpers.setStatus(txtStatus, "Local google sign-in successful, signing into server...");

            Activity activity = this;
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", getResources().getString(R.string.google_key))
                .add("client_secret", getResources().getString(R.string.google_secret))
                .add("redirect_uri","")
                .add("code", acct.getServerAuthCode())
                .build();
            Request request = new Request.Builder()
                .url("https://www.googleapis.com/oauth2/v4/token")
                .post(requestBody)
                .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    UiHelpers.setStatus(txtStatus, "Failed to retrieve AccessToken from Google");
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String json = response.body().string();
                    JsonObject obj = JsonUtils.toJsonObject(json);
                    String accessToken = obj.get("access_token").getAsString();
                    App.get().saveGoogleAccessToken(accessToken);

                    App.get().getServiceClient().postAsync(new dtos.Authenticate()
                        .setProvider("GoogleOAuth")
                        .setAccessToken(accessToken)
                        .setRememberMe(true),
                        r -> {
                            UiHelpers.setStatus(txtStatus, "Server google sign-in successful, opening chat...");
                            Intent intent = new Intent(activity, MainActivity.class);
                            stopProgressBar();
                            startActivity(intent);
                        },
                        error -> {
                            UiHelpers.setStatusError(txtStatus, "Server google sign-in failed", error);
                            stopProgressBar();
                        });
                }
            });
        }
    }

    private void startProgressBar(){
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            animation.start();
        });
    }

    private void stopProgressBar(){
        runOnUiThread(() -> {
            progressBar.clearAnimation();
            progressBar.setVisibility(View.INVISIBLE);
        });
    }
}
