package com.riad.core;

import com.fasterxml.jackson.core.JsonParseException;
import com.riad.core.io.ReadFileException;
import com.riad.core.io.WebRootHandler;
import com.riad.http.*;
import com.riad.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class HttpConnectionWorkerThread extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnectionWorkerThread.class);
    private Socket socket;
    private WebRootHandler webRootHandler;
    private HttpParser httpParser = new HttpParser();

    public HttpConnectionWorkerThread(Socket socket , WebRootHandler webRootHandler) {
        this.socket = socket;
        this.webRootHandler = webRootHandler;
    }

    @Override
    public void run() {
        InputStream inputStream  = null;
        OutputStream outputStream = null;

        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();

            HttpRequest request = httpParser.parseHttpRequest(inputStream);
            HttpResponse response = handleRequest(request);

            outputStream.write(response.getResponseBytes());

            LOGGER.info(" * Connection Processing Finished.");
        } catch (IOException e) {
            LOGGER.error("Problem with communication", e);
        } catch (HttpParsingException e) {
            LOGGER.info("Bag Request", e);

            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(HttpVersion.HTTP_1_1.LITERAL)
                    .statusCode(e.getErrorCode())
                    .build();
            try {
                outputStream.write(response.getResponseBytes());
            } catch (IOException ex) {
                LOGGER.error("Problem with communication", e);
            }

        } finally {
            if (inputStream!= null) {
                try {
                    inputStream.close();
                } catch (IOException e) {}
            }
            if (outputStream!=null) {
                try {
                    outputStream.close();
                } catch (IOException e) {}
            }
            if (socket!= null) {
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }
    }

    private HttpResponse handleRequest(HttpRequest request) {
        return switch (request.getMethod()) {
            case GET -> {
                LOGGER.info(" * GET Request");
                yield handleGetRequest(request, true);
            }
            case HEAD -> {
                LOGGER.info(" * HEAD Request");
                yield handleGetRequest(request, false);
            }
            case POST -> {
                LOGGER.info(" * POST Request");
                yield handlePostRequest(request);
            }
            case PUT -> {
                LOGGER.info(" * PUT Request");
                yield handlePutRequest(request);
            }
            case DELETE -> {
                LOGGER.info(" * DELETE Request");
                yield handleDeleteRequest(request);
            }
            default -> new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.SERVER_ERROR_501_NOT_IMPLEMENTED)
                    .build();
        };

    }

    private HttpResponse handlePostRequest(HttpRequest request) {
        try {
            byte[] body = request.getMessageBody();
            String content = new String(body);

            if (body == null || content.isEmpty() || Json.parse(content).isValueNode()) {
                return new HttpResponse.Builder()
                        .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                        .statusCode(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST)
                        .build();
            }

            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.OK)
                    .messageBody(body)
                    .addHeader(HttpHeaderName.CONTENT_TYPE.headerName, "plain/text")
                    .build();
        }catch (JsonParseException e){
            LOGGER.error("JSON Format incorrect", e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST)
                    .build();
        }

        catch(Exception e) {
            LOGGER.error("Error processing POST request", e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.SERVER_ERROR_500_INTERNAL_SERVER_ERROR)
                    .build();
        }
    }


    private HttpResponse handlePutRequest(HttpRequest request) {
        try {
            byte[] body = request.getMessageBody();
            String content = new String(body);
            System.out.println("Test ; " + Json.parse(content).isValueNode());
            if (body == null || content.isEmpty() || Json.parse(content).isValueNode()) {
                return new HttpResponse.Builder()
                        .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                        .statusCode(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST)
                        .build();
            }

            String target = request.getRequestTarget();
            LOGGER.info("PUT Target: {}, Body: {}", target, content);

            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.OK)
                    .messageBody("Resource updated".getBytes())
                    .build();
        }catch (JsonParseException e){
            LOGGER.error("JSON Format incorrect", e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST)
                    .build();
        }
        catch (Exception e) {
            LOGGER.error("Error processing PUT request", e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.SERVER_ERROR_500_INTERNAL_SERVER_ERROR)
                    .build();
        }
    }


    private HttpResponse handleDeleteRequest(HttpRequest request) {
        try {
            String target = request.getRequestTarget();
            LOGGER.info("DELETE Target: {}", target);

            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.OK)
                    .messageBody("Resource deleted".getBytes())
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error processing DELETE request", e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.SERVER_ERROR_500_INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private HttpResponse handleGetRequest(HttpRequest request, boolean setMessageBody) {
        try {
            LOGGER.debug("Method GET invoked");
            HttpResponse.Builder builder = new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.OK)
                    .addHeader(HttpHeaderName.CONTENT_TYPE.headerName, webRootHandler.getFileMimeType(request.getRequestTarget()));

            if (setMessageBody) {
                byte[] messageBody = webRootHandler.getFileByteArrayData(request.getRequestTarget());
                builder.addHeader(HttpHeaderName.CONTENT_LENGTH.headerName, String.valueOf(messageBody.length))
                        .messageBody(messageBody);
            }

            return builder.build();

        } catch (FileNotFoundException e) {

            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.CLIENT_ERROR_404_NOT_FOUND)
                    .build();

        } catch (ReadFileException e) {

            return new HttpResponse.Builder()
                    .httpVersion(request.getBestCompatibleHttpVersion().LITERAL)
                    .statusCode(HttpStatusCode.SERVER_ERROR_500_INTERNAL_SERVER_ERROR)
                    .build();
        }

    }
}
