package com.demo.aadharscanner;

import java.io.*;
import java.math.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.Charset;
class Parse{
    private static final String TAG = "Parse";
    private final String rawScanText;
    private static final int NUMBER_OF_PARAMS_IN_SECURE_QR_CODE = 15;
    private final byte[] array;
    private int mobileEmailPresentBit = 3;
    Parse(String rawText){
        this.rawScanText = rawText;
        //rawscan to bytearray
        //decompress the byte array
        this.array = getByteArray();
    }
    public Map<String,String> getAllAttributes(){
        //get attributes
        return getAttributes(array);
    }

    private byte[] getByteArray(){
        BigInteger bigIntCode = new BigInteger(rawScanText);

        byte[] array = bigIntCode.toByteArray(); //input byte array for Inflator

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(array);
            GZIPInputStream gis = new GZIPInputStream(in);
            byte[] buffer = new byte[1024];
            int len;
            while((len = gis.read(buffer)) != -1){
                os.write(buffer, 0, len);
            }
            os.close();
            gis.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return os.toByteArray();
    }

    private Map<String,String> getAttributes(byte[] byteText){
        int[] delimeterPosition = locateDelimiters(byteText);
        Map<String,String> attributes = new HashMap<>();
        try {
            mobileEmailPresentBit = Integer.parseInt(getValue(byteText,0,1));
        }
        catch (NumberFormatException nfe){ nfe.printStackTrace();}
        String[] attributeNames = {"referenceid", "name","dob","gender","careof","district","landmark","house","location",
                "pincode","PO","state","street","sub district","VTC"};

        for(int i=0;i<NUMBER_OF_PARAMS_IN_SECURE_QR_CODE;i++){
            attributes.put(attributeNames[i], getValue(byteText, delimeterPosition[i] + 1, delimeterPosition[i+1]));
        }
        attributes.put("mobileEmailStatus",String.valueOf(mobileEmailPresentBit));
        return attributes;
    }

    private static int[] locateDelimiters(byte[] msgInBytes) {
        int[] delimiters = new int[NUMBER_OF_PARAMS_IN_SECURE_QR_CODE + 1];
        int index = 0;
        int delimiterIndex;
        for (int i = 0; i <= NUMBER_OF_PARAMS_IN_SECURE_QR_CODE; i++) {
            delimiterIndex = getNextDelimiterIndex(msgInBytes, index);
            delimiters[i] = delimiterIndex;
            index = delimiterIndex + 1;
        }
        return delimiters;
    }

    private static int getNextDelimiterIndex(byte[] msgInBytes, int index) {
        int i = index;
        for (; i < msgInBytes.length; i++) {
            if (msgInBytes[i] == -1) {
                break;
            }
        }
        return i;
    }

    private String getValue(byte[] msg, int start, int end) {
        return new String(Arrays.copyOfRange(msg, start, end), Charset.forName("ISO-8859-1"));
    }

    public Map<String,String> getMobileAndEmail(){
        Map<String,String> otherAttributes = new HashMap<>();
//        String result ="";
//        byte[] signature = Arrays.copyOfRange(array, array.length-256, array.length -1);
        byte[] mobile;
        byte[] email;
        switch (mobileEmailPresentBit){
            case 3:
                mobile = Arrays.copyOfRange(array, array.length-256-32, array.length-256);
                email = Arrays.copyOfRange(array, array.length-256-32-32, array.length-256-32);
                otherAttributes.put("mobile",byteToHex(mobile));
                otherAttributes.put("email",byteToHex(email));
                break;
            case 2:
                email = Arrays.copyOfRange(array, array.length-256-32-32, array.length-1-256-32);
                otherAttributes.put("email",byteToHex(email));
                break;
            case 1:
                mobile = Arrays.copyOfRange(array, array.length-256-32, array.length-1-256);
                otherAttributes.put("mobile",byteToHex(mobile));
                break;
            case 0:
                mobile = null;
                email = null;
                break;
        }
        return otherAttributes;
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

    public InputStream getUserPhotoData(){
        /*At last read the photo from index (VTC delimeter value of “255” + 1)
         to index (Byte array length – 1- 256 – (if mobile present then -32 if email present then -32 ))
        */
        int[] delimeters = locateDelimiters(array);
        int endIndex = mobileEmailPresentBit==0 ? array.length-256 : mobileEmailPresentBit==1 ? array.length-256-32 : array.length-256-32-32;
        byte[] photoData = Arrays.copyOfRange(array, delimeters[NUMBER_OF_PARAMS_IN_SECURE_QR_CODE] + 1, endIndex);
        return new ByteArrayInputStream(photoData);
    }
}