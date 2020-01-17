package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Map;

/**
 * A request for upload file and parameters via multipart
 *
 */

public class MutipartRequest extends Request<NetworkResponse> {
    private final String twoHyphens = "--";
    private final String lineEnd = "\r\n";
    private final String boundary = "apiclient-" + System.currentTimeMillis();

    private Response.Listener<NetworkResponse> listener;
    private Response.ErrorListener errorListener;
    private Map<String, String> headers;

    public MutipartRequest(int method, String url,
                           Response.Listener<NetworkResponse> listener,
                           Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = listener;
        this.errorListener = errorListener;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return (headers != null) ? headers : super.getHeaders();
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data;boundary=" + boundary;
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        try {
            // populate text payload
            Map<String, String> params = getParams();
            if (params != null && params.size() > 0) {
                textParse(dataOutputStream, params, getParamsEncoding());
            }

            // populate each single data byte payload
            Map<String, MultipartData> data = getByteData();
            if (data != null && data.size() > 0) {
                dataParse(dataOutputStream, data);
            }

            // populate array data byte payload with single label
            Map<String, ArrayList<MultipartData>> arrData = getArrayByteData();
            if (arrData != null && arrData.size() > 0) {
                arrDataParse(dataOutputStream, arrData);
            }

            // close multipart form data after text and file data
            dataOutputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
        try {
            return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
        }
        catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(NetworkResponse response) {
        this.listener.onResponse(response);
    }

    @Override
    public void deliverError(VolleyError error) {
        this.errorListener.onErrorResponse(error);
    }

    //region Processing Byte part
    /**
     * Custom method handle data payload.
     *
     * @return Map data part label with data byte
     * @throws AuthFailureError
     */
    protected Map<String, MultipartData> getByteData() throws AuthFailureError {
        return null;
    }

    /**
     * Custom method handle multiple data payload.
     *
     * @return Map data part single label with array data bytes
     * @throws AuthFailureError
     */
    protected Map<String, ArrayList<MultipartData>> getArrayByteData() throws AuthFailureError {
        return null;
    }

    /**
     * Parse data into data output stream.
     *
     * @param dataOutputStream data output stream handle file attachment
     * @param data             loop through data
     * @throws IOException
     */
    private void dataParse(DataOutputStream dataOutputStream, Map<String, MultipartData> data) throws IOException {
        for (Map.Entry<String, MultipartData> entry : data.entrySet()) {
            buildDataPart(dataOutputStream, entry.getValue(), entry.getKey());
        }
    }

    /**
     * Parse array data into data output stream.
     *
     * @param dataOutputStream data output stream handle file attachment
     * @param arrData             loop through data
     * @throws IOException
     */
    private void arrDataParse(DataOutputStream dataOutputStream, Map<String, ArrayList<MultipartData>> arrData) throws IOException {
        for (Map.Entry<String, ArrayList<MultipartData>> entry : arrData.entrySet()) {
            ArrayList<MultipartData> listData = entry.getValue();
            for (MultipartData val :  listData) {
                buildDataPart(dataOutputStream, val, entry.getKey());
            }
        }
    }

    /**
     * Write data file into header and data output stream.
     *
     * @param dataOutputStream data output stream handle data parsing
     * @param dataFile         data byte as MultipartData from collection
     * @param inputName        name of data input
     * @throws IOException
     */
    private void buildDataPart(DataOutputStream dataOutputStream, MultipartData dataFile, String inputName) throws IOException {
        dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd);
        dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"" +
                inputName + "\"; filename=\"" + dataFile.getFileName() + "\"" + lineEnd);




        if (dataFile.getType() != null) {
            boolean isDataFileEmpty;
            if (android.os.Build.VERSION.SDK_INT >= 9){
                isDataFileEmpty = dataFile.getType().trim().isEmpty();
            } else{
                isDataFileEmpty = dataFile.getType().trim().equals("");
            }
            if (!isDataFileEmpty) {
                dataOutputStream.writeBytes("Content-Type: " + dataFile.getType() + lineEnd);
            }
        }
        dataOutputStream.writeBytes(lineEnd);

        ByteArrayInputStream fileInputStream = new ByteArrayInputStream(dataFile.getContent());
        int bytesAvailable = fileInputStream.available();

        int maxBufferSize = 1024 * 1024;
        int bufferSize = Math.min(bytesAvailable, maxBufferSize);
        byte[] buffer = new byte[bufferSize];

        int bytesRead = fileInputStream.read(buffer, 0, bufferSize);

        while (bytesRead > 0) {
            dataOutputStream.write(buffer, 0, bufferSize);
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }

        dataOutputStream.writeBytes(lineEnd);
    }

    //endregion

    //region Processing Text parts

    /**
     * Parse string map into data output stream by key and value.
     *
     * @param dataOutputStream data output stream handle string parsing
     * @param params           string inputs collection
     * @param encoding         encode the inputs, default UTF-8
     * @throws IOException
     */
    private void textParse(DataOutputStream dataOutputStream, Map<String, String> params, String encoding) throws IOException {
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                buildTextPart(dataOutputStream, entry.getKey(), entry.getValue(), encoding);
            }
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Encoding not supportted: " + encoding, e);
        }
    }

    /**
     * Write string data into header and data output stream.
     *
     * @param dataOutputStream data output stream handle string parsing
     * @param parameterName    name of input
     * @param parameterValue   value of input
     * @throws IOException
     */
    private void buildTextPart(DataOutputStream dataOutputStream, String parameterName, String parameterValue, String encoding) throws IOException {
        dataOutputStream.writeBytes(twoHyphens + boundary + lineEnd);
        dataOutputStream.writeBytes("Content-Disposition: form-data; name=\"" + parameterName + "\"" + lineEnd);
        dataOutputStream.writeBytes(lineEnd);
        dataOutputStream.write((parameterValue+lineEnd).getBytes(encoding));
    }
    //endregion

    class MultipartData {
        private String fileName;
        private byte[] content;
        private String type;

        public MultipartData() {}

        public MultipartData(String name, byte[] data) {
            fileName = name;
            content = data;
        }

        public MultipartData(String name, byte[] data, String mimeType) {
            fileName = name;
            content = data;
            type = mimeType;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }

        public String getType() {
            return type;
        }
    }
}
