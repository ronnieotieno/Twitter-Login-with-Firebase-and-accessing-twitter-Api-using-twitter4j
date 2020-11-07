package com.example.ronniestwitterapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.ronniestwitterapp.TwitterCredentials.CONSUMER_KEY
import com.example.ronniestwitterapp.TwitterCredentials.CONSUMER_SECRET
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import twitter4j.StatusUpdate
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream


class MainActivity : AppCompatActivity() {

    var token: String? = null
    var secret: String? = null
    var file: File? = null
    lateinit var mTwitter: Twitter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkIfTwitterUserIsLoggedIn()

    }

    /**
     * If the file doesnt exist then the user isnt logged in, we log in the user
     */

    private fun checkIfTwitterUserIsLoggedIn() {

        //The shared pref is stored in this path /data/data/{your package}/shared_prefs/{shared pref name}.xml
        //in my case its /data/data/com.example.ronniestwitterapp/shared_prefs/TwitterCredentials.xml

        val user = File(
            getString(R.string.shared_pref_path)
        )
        if (!user.exists()) {
            signInTwitter()
        } else {
            val prefs = getSharedPreferences("TwitterCredentials", MODE_PRIVATE)
            token =
                prefs.getString("token", null)

            secret = prefs.getString("secret", null)


            buildTwitter()

        }

    }

    private fun buildTwitter() {
        val cb = ConfigurationBuilder()
        cb.setDebugEnabled(true)
            .setOAuthConsumerKey(CONSUMER_KEY)
            .setOAuthConsumerSecret(CONSUMER_SECRET)
            .setOAuthAccessToken(token)
            .setOAuthAccessTokenSecret(secret)
        val tf = TwitterFactory(cb.build())
        mTwitter = tf.instance

        startSendingTweet()
    }

    /**
     * Here we are checking the edittext and if tweet is less or than 280 characters.
     */
    private fun startSendingTweet() {
        tweet_editText.addTextChangedListener {
            val text = tweet_editText.text.toString()

            if (text.length >= 280) {
                Toast.makeText(this, text.length.toString(), Toast.LENGTH_SHORT).show()
            }
        }

        sendTweet.setOnClickListener {
            val text = tweet_editText.text.toString()

            if (text.length > 280) {
                Toast.makeText(this, "Tweet too Long", Toast.LENGTH_SHORT).show()
            } else {

                lifecycleScope.launch(Dispatchers.IO) {
                    val status = tweet_editText.text.toString().trim()

                    if (status.isNotEmpty()) tweet(status)
                }

            }
        }

        add_image.setOnClickListener {
            requestStoragePermission()
        }

    }


    private suspend fun tweet(message: String?) {
        try {
            val status = StatusUpdate(message)

            file?.let {
                status.setMedia(it)
            }
            mTwitter.updateStatus(status)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Tweet Posted", Toast.LENGTH_SHORT).show()
            }
        } catch (e: TwitterException) {
            e.message?.let { Log.d("TweetError", it) }

        }
    }


    private fun signInTwitter() {
        val provider = OAuthProvider.newBuilder("twitter.com")
        FirebaseAuth.getInstance()
            .startActivityForSignInWithProvider( /* activity= */this, provider.build())
            .addOnSuccessListener { authResult ->
                val editor = getSharedPreferences("TwitterCredentials", MODE_PRIVATE).edit()
                editor.putString("token", (authResult.credential as OAuthCredential).accessToken)
                editor.putString("secret", (authResult.credential as OAuthCredential).secret)
                editor.apply()

                token = (authResult.credential as OAuthCredential).accessToken
                secret = (authResult.credential as OAuthCredential).secret
                buildTwitter()

            }
            .addOnFailureListener {
                Log.d("TwitterlogIn", "Failed ${it.message}")
            }
    }

    private fun requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                102
            )


        } else {
            getImageFromStorage()
        }
    }

    private fun getImageFromStorage() {

        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        this.startActivityForResult(
            Intent.createChooser(intent, "Select Profile Image"),
            101
        )

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getImageFromStorage()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 101 && resultCode == Activity.RESULT_OK && data != null && data.data != null) {


            //creating temp file for the image we plan to tweet
            val initialStream: InputStream = contentResolver.openInputStream(data.data!!)!!

            val buffer = ByteArray(initialStream.available())
            initialStream.read(buffer)

            val tempFile = File.createTempFile("tweetpic", ".jpg")
            val outStream: OutputStream = FileOutputStream(tempFile)
            outStream.write(buffer)

            file = tempFile
            add_image.text = "Image Selected"
        }

    }
}