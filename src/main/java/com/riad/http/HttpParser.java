package com.riad.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpParser {

    private final static Logger LOGGER = LoggerFactory.getLogger(HttpParser.class);
    private static final int SP = 0x20; // 32
    private static final int CR = 0x0D; // 13
    private static final int LF = 0x0A; // 10

    public HttpRequest parseHttpRequest(InputStream inputStream) throws IOException {
        InputStreamReader reader = new InputStreamReader(inputStream , StandardCharsets.US_ASCII);

        HttpRequest request = new HttpRequest();

        parseRequestLine(reader , request);
        parseHeaders(reader , request);
        parseBody(reader , request);

        return request;
    }

    private void parseRequestLine(InputStreamReader reader, HttpRequest request) throws IOException, HttpParsingException {
        StringBuilder sb = new StringBuilder();

        boolean methodParsed = false;
        boolean requestTargetParsed = false;

        // TODO validate URI size!

        int _byte;
        while ((_byte = reader.read()) >=0) {
            if (_byte == CR) {
                _byte = reader.read();
                if (_byte == LF) {
                    LOGGER.debug("Request Line VERSION to Process : {}" , sb.toString());
                    if (!methodParsed || !requestTargetParsed) {
                        throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                    }

                    try {
                        request.setHttpVersion(sb.toString());
                    } catch (BadHttpVersionException e) {
                        throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                    }

                    return;
                } else {
                    throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                }
            }

            if (_byte == SP) {
                if (!methodParsed) {
                    LOGGER.debug("Request Line METHOD to Process : {}" , sb.toString());
                    request.setMethod(sb.toString());
                    methodParsed = true;
                } else if (!requestTargetParsed) {
                    LOGGER.debug("Request Line REQ TARGET to Process : {}" , sb.toString());
                    request.setRequestTarget(sb.toString());
                    requestTargetParsed = true;
                } else {
                    throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                }
                sb.delete(0, sb.length());
            } else {
                sb.append((char)_byte);
                if (!methodParsed) {
                    if (sb.length() > HttpMethod.MAX_LENGTH) {
                        throw new HttpParsingException(HttpStatusCode.SERVER_ERROR_501_NOT_IMPLEMENTED);
                    }
                }
            }
        }

    }

    private void parseHeaders(InputStreamReader reader, HttpRequest request) throws IOException, HttpParsingException {
        StringBuilder processingDataBuffer = new StringBuilder();
        boolean crlfFound = false;

        int _byte;
        while ((_byte = reader.read()) >=0) {
            if (_byte == CR) {
                _byte = reader.read();
                if (_byte == LF) {
                    if (!crlfFound) {
                        crlfFound = true;

                        // Do Things like processing
                        processSingleHeaderField(processingDataBuffer, request);
                        // Clear the buffer
                        processingDataBuffer.delete(0, processingDataBuffer.length());
                    } else {
                        // Two CRLF received, end of Headers section
                        return;
                    }
                } else {
                    throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                }
            } else {
                crlfFound = false;
                // Append to Buffer
                processingDataBuffer.append((char)_byte);
            }
        }
    }

    private void processSingleHeaderField(StringBuilder processingDataBuffer, HttpRequest request) throws HttpParsingException {
        String rawHeaderField = processingDataBuffer.toString();
        Pattern pattern = Pattern.compile("^(?<fieldName>[!#$%&’*+\\-./^_‘|˜\\dA-Za-z]+):\\s?(?<fieldValue>[!#$%&’*+\\-./^_‘|˜(),:;<=>?@[\\\\]{}\" \\dA-Za-z]+)\\s?$");

        Matcher matcher = pattern.matcher(rawHeaderField);
        if (matcher.matches()) {
            // We found a proper header
            String fieldName = matcher.group("fieldName");
            String fieldValue = matcher.group("fieldValue");
            request.addHeader(fieldName, fieldValue);
        } else{
            throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
        }
    }

    private void parseBody(InputStreamReader reader, HttpRequest request) throws IOException, HttpParsingException {
        // Check if the request has a body (e.g., POST or PUT method)
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            try {
                int contentLength = Integer.parseInt(contentLengthHeader);
                if (contentLength < 0) {
                    throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                }

                // Read the body content into a byte array
                byte[] bodyBuffer = new byte[contentLength];
                for (int i = 0; i < contentLength; i++) {
                    int _byte = reader.read();
                    if (_byte == -1) {
                        throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                    }
                    bodyBuffer[i] = (byte) _byte;
                }

                request.setMessageBody(bodyBuffer); // Set the body
            } catch (NumberFormatException e) {
                throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
            }
        } else if ("chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"))) {
            // Handle chunked transfer encoding
            ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
            StringBuilder chunkSizeBuffer = new StringBuilder();

            int _byte;
            while ((_byte = reader.read()) >= 0) {
                if (_byte == CR) {
                    _byte = reader.read();
                    if (_byte == LF) {
                        // End of chunk size or content
                        if (chunkSizeBuffer.length() == 0) {
                            // Reached the end of the body
                            break;
                        }

                        int chunkSize = Integer.parseInt(chunkSizeBuffer.toString().trim(), 16);
                        chunkSizeBuffer.delete(0, chunkSizeBuffer.length());

                        if (chunkSize == 0) {
                            // Final chunk
                            break;
                        }

                        // Read the chunk data
                        for (int i = 0; i < chunkSize; i++) {
                            int chunkByte = reader.read();
                            if (chunkByte == -1) {
                                throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                            }
                            bodyBuffer.write(chunkByte);
                        }

                        // Consume the CRLF after the chunk
                        _byte = reader.read();
                        if (_byte != CR) {
                            throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                        }
                        _byte = reader.read();
                        if (_byte != LF) {
                            throw new HttpParsingException(HttpStatusCode.CLIENT_ERROR_400_BAD_REQUEST);
                        }
                    } else {
                        chunkSizeBuffer.append((char) _byte);
                    }
                } else {
                    chunkSizeBuffer.append((char) _byte);
                }
            }

            request.setMessageBody(bodyBuffer.toByteArray()); // Set the body
        }
    }

}
