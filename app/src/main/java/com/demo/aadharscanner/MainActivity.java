package com.demo.aadharscanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gemalto.jp2.JP2Decoder;
import com.google.android.gms.vision.barcode.Barcode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String TAG= "MainActivity";
    Button scanbtn, verifyMobile, verifyEmail;
    TextView result, name, gender, dob, address;
    EditText mobileInput, emailInput;
    ImageView userImage, isMobilVerified, isEmailVerified;
    View secureQrCodePanel;
    private String scannedMobile, scannedEmail;
    public static final int REQUEST_CODE = 100;
    public static final int PERMISSION_REQUEST = 200;
    private static int LAST_DIGIT_OF_UID=-1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanbtn = (Button) findViewById(R.id.scanbtn);
        result = (TextView) findViewById(R.id.result);
        secureQrCodePanel = (View)findViewById(R.id.secureQrCodePane);
        secureQrCodePanel.setVisibility(View.INVISIBLE);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST);
        }
        scanbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                startActivityForResult(intent, REQUEST_CODE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CODE && resultCode == RESULT_OK){
            if(data != null){
                final Barcode barcode = data.getParcelableExtra("barcode");
                result.post(new Runnable() {
                    @Override
                    public void run() {
                        assert barcode != null;
                        String qrCodeResult=barcode.displayValue;
                        findQrCodeType(qrCodeResult);
                    }
                });
            }
        }
    }

    private void findQrCodeType(String qrCodeResult){
        if(qrCodeResult.startsWith("<?xml"))
            parseXML(qrCodeResult);
        else if(qrCodeResult.matches("[0-9]*"))
            parseSecureQRCode(qrCodeResult);
        else
            result.setText(qrCodeResult);
    }

    private void parseXML(String qrCodeResult) {
        String warning = "Your aadhar QR code is very old and is not secure. " +
                "You will see the resultfor 3 seconds only\n\n";
        result.setText(warning.concat(qrCodeResult));
        result.setTextColor(Color.RED);
        new CountDownTimer(3000,1000) {
            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                result.setVisibility(View.GONE);
            }
        }.start();
    }

    private void parseSecureQRCode(String qrCodeResult) {
        Parse p = new Parse(qrCodeResult);
        Map<String,String> userAttributes = p.getAllAttributes();

        //4th digit of ref id is last digit of UID. Will be used to verify mobile and email
        LAST_DIGIT_OF_UID = Integer.parseInt(Objects.requireNonNull(
                userAttributes.get("referenceid")).substring(3,4));
        //get status of mobile and email presence
        int mobileEmailStatus = Integer.parseInt(Objects.requireNonNull(
                userAttributes.get("mobileEmailStatus")));
        boolean isMobilePresent = mobileEmailStatus==2 || mobileEmailStatus==3;
        boolean isEmailPresent = mobileEmailStatus==1 || mobileEmailStatus==3;

        //get hashed value of mobile and email
        Map<String,String> hashedAttributes = p.getMobileAndEmail();
        if(isMobilePresent) scannedMobile = hashedAttributes.get("mobile");
        if(isEmailPresent) scannedEmail = hashedAttributes.get("email");

        //initialize the views
        initScanResultLayout(isMobilePresent, isEmailPresent);

        //set values
        displayValues(userAttributes);

        //parse and show the jp2 image of user
        InputStream is = p.getUserPhotoData();
        DecodeJP2ImageAsyncTask asyncTask = new DecodeJP2ImageAsyncTask(is,userImage);
        asyncTask.execute();
    }

    private void displayValues(Map<String, String> userAttributes) {
        name.setText(userAttributes.get("name"));
        gender.setText(userAttributes.get("gender"));
        dob.setText(userAttributes.get("dob"));
        String sep = ", ";
        String adressString = userAttributes.get("careof") + sep +
                userAttributes.get("house") + sep +
                userAttributes.get("street") + sep +
                userAttributes.get("location") + sep +
                userAttributes.get("landmark") +sep +
                userAttributes.get("VTC") + sep +
                userAttributes.get("district") + "\n" +
                userAttributes.get("state") + "-" + userAttributes.get("pincode");
        address.setText(adressString);
    }

    private void initScanResultLayout(boolean isMobilePresent, boolean isEmailPresent) {
        result.setText(R.string.infosecureqrcode);
        name = (TextView)findViewById(R.id.tvName);
        gender = (TextView)findViewById(R.id.tvGender);
        dob = (TextView)findViewById(R.id.tvDob);
        address = (TextView)findViewById(R.id.tvAddress);
        mobileInput = (EditText)findViewById(R.id.etMobile);
        emailInput = (EditText)findViewById(R.id.etEmail);
        userImage = (ImageView)findViewById(R.id.usrImage);
        isMobilVerified = (ImageView)findViewById(R.id.isMobileVerified);
        isEmailVerified = (ImageView)findViewById(R.id.isEmailVerified);
        verifyEmail = (Button)findViewById(R.id.btnVerifyEmail);
        verifyMobile = (Button)findViewById(R.id.btnVerifyMobile);

        verifyMobile.setEnabled(true);
        verifyEmail.setEnabled(true);
        //handle mobile/email absence
        if(!isMobilePresent){
            //show mobile not present in edit text
            mobileInput.setText(R.string.noMobile);
            mobileInput.setEnabled(false);
            //hide the verify button
            verifyMobile.setVisibility(View.GONE);
        }
        if(!isEmailPresent){
            //show mobile not present in edit text
            emailInput.setText(R.string.noEmail);
            emailInput.setEnabled(false);
            //hide the verify button
            verifyEmail.setVisibility(View.GONE);
        }
        isMobilVerified.setVisibility(View.GONE);
        isEmailVerified.setVisibility(View.GONE);

        secureQrCodePanel.setVisibility(View.VISIBLE);
    }

    public void verifyMobile(View view){
        String inputMobile = mobileInput.getText().toString().trim();
        if(inputMobile.length() != 10)
            mobileInput.setError("Invalid mobile no.");
        boolean mobileMatched = false;

        try {
            mobileMatched = hashNTimes(inputMobile, LAST_DIGIT_OF_UID).equals(scannedMobile);
        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }

        if(mobileMatched){
            isMobilVerified.setImageDrawable(getDrawable(R.drawable.ic_baseline_check_circle_24));
        }
        else {
            isMobilVerified.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24));
        }
        isMobilVerified.setVisibility(View.VISIBLE);
    }

    public void verifyEmail(View view){
        String inputEmail = emailInput.getText().toString().trim();
        if(inputEmail.isEmpty())
            emailInput.setError("Invalid Email id");
        boolean emailMatched = false;
        try {
           emailMatched = hashNTimes(inputEmail, LAST_DIGIT_OF_UID).equals(scannedEmail);

        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }

        if(emailMatched){
            isEmailVerified.setImageDrawable(getDrawable(R.drawable.ic_baseline_check_circle_24));
        }
        else {
            isEmailVerified.setImageDrawable(getDrawable(R.drawable.ic_baseline_close_24));
        }
        isEmailVerified.setVisibility(View.VISIBLE);
    }

    private byte[] getSHA(String input) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    private String byteToHex(byte[] b){
        final char[] HEX_ALPHABET = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[b.length * 2];
        for(int j = 0; j< b.length ; j++){
            int v = b[j] & 0xff;
            hexChars[j * 2] = HEX_ALPHABET[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ALPHABET[v & 0x0f];
        }
        return new String(hexChars);
    }

    private String hashNTimes(String input, int n) throws NoSuchAlgorithmException {
        String hashedString ="";
        if(n == 0 || n == 1) {
            hashedString = byteToHex(getSHA(input));
        }
        else{
            while (n-- > 0){
                hashedString = byteToHex(getSHA(input));
            }
        }
        return hashedString;
    }

    private static class DecodeJP2ImageAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        private final InputStream is;
        private final ImageView imageView;
        public DecodeJP2ImageAsyncTask(final InputStream is, final ImageView imageView){
            this.is = is;
            this.imageView = imageView;
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            super.onPostExecute(bmp);
            if(bmp!=null)
                imageView.setImageBitmap(bmp);
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            JP2Decoder decoder = new JP2Decoder(is);
            return decoder.decode();
        }
    }
}
