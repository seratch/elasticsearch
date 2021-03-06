/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.security.action.user;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.support.WriteRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.ValidationException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.xpack.core.security.authc.support.Hasher;
import org.elasticsearch.xpack.core.security.support.Validation;
import org.elasticsearch.protocol.xpack.security.User;
import org.elasticsearch.xpack.core.security.xcontent.XContentUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class PutUserRequestBuilder extends ActionRequestBuilder<PutUserRequest, PutUserResponse>
        implements WriteRequestBuilder<PutUserRequestBuilder> {

    public PutUserRequestBuilder(ElasticsearchClient client) {
        this(client, PutUserAction.INSTANCE);
    }

    public PutUserRequestBuilder(ElasticsearchClient client, PutUserAction action) {
        super(client, action, new PutUserRequest());
    }

    public PutUserRequestBuilder username(String username) {
        request.username(username);
        return this;
    }

    public PutUserRequestBuilder roles(String... roles) {
        request.roles(roles);
        return this;
    }

    public PutUserRequestBuilder password(char[] password, Hasher hasher) {
        if (password != null) {
            Validation.Error error = Validation.Users.validatePassword(password);
            if (error != null) {
                ValidationException validationException = new ValidationException();
                validationException.addValidationError(error.toString());
                throw validationException;
            }
            request.passwordHash(hasher.hash(new SecureString(password)));
        } else {
            request.passwordHash(null);
        }
        return this;
    }

    public PutUserRequestBuilder metadata(Map<String, Object> metadata) {
        request.metadata(metadata);
        return this;
    }

    public PutUserRequestBuilder fullName(String fullName) {
        request.fullName(fullName);
        return this;
    }

    public PutUserRequestBuilder email(String email) {
        request.email(email);
        return this;
    }

    public PutUserRequestBuilder passwordHash(char[] passwordHash) {
        request.passwordHash(passwordHash);
        return this;
    }

    public PutUserRequestBuilder enabled(boolean enabled) {
        request.enabled(enabled);
        return this;
    }

    /**
     * Populate the put user request using the given source and username
     */
    public PutUserRequestBuilder source(String username, BytesReference source, XContentType xContentType, Hasher hasher) throws
        IOException {
        Objects.requireNonNull(xContentType);
        username(username);
        // EMPTY is ok here because we never call namedObject
        try (InputStream stream = source.streamInput();
             XContentParser parser = xContentType.xContent()
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, stream)) {
            XContentUtils.verifyObject(parser);
            XContentParser.Token token;
            String currentFieldName = null;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (User.Fields.PASSWORD.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        String password = parser.text();
                        char[] passwordChars = password.toCharArray();
                        password(passwordChars, hasher);
                        Arrays.fill(passwordChars, (char) 0);
                    } else {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type string, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.PASSWORD_HASH.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        char[] passwordChars = parser.text().toCharArray();
                        passwordHash(passwordChars);
                    } else {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type string, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.ROLES.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        roles(Strings.commaDelimitedListToStringArray(parser.text()));
                    } else {
                        roles(XContentUtils.readStringArray(parser, false));
                    }
                } else if (User.Fields.FULL_NAME.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        fullName(parser.text());
                    } else if (token != XContentParser.Token.VALUE_NULL) {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type string, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.EMAIL.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        email(parser.text());
                    } else if (token != XContentParser.Token.VALUE_NULL) {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type string, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.METADATA.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.START_OBJECT) {
                        metadata(parser.map());
                    } else {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type object, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.ENABLED.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == XContentParser.Token.VALUE_BOOLEAN) {
                        enabled(parser.booleanValue());
                    } else {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type boolean, but found [{}] instead", currentFieldName, token);
                    }
                } else if (User.Fields.USERNAME.match(currentFieldName, parser.getDeprecationHandler())) {
                    if (token == Token.VALUE_STRING) {
                        if (username.equals(parser.text()) == false) {
                            throw new IllegalArgumentException("[username] in source does not match the username provided [" +
                                    username + "]");
                        }
                    } else {
                        throw new ElasticsearchParseException(
                                "expected field [{}] to be of type string, but found [{}] instead", currentFieldName, token);
                    }
                } else {
                    throw new ElasticsearchParseException("failed to parse add user request. unexpected field [{}]", currentFieldName);
                }
            }
            return this;
        }
    }

}
