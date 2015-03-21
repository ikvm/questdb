/*
 * Copyright (c) 2014. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.lang.parser;

import com.nfsdb.collections.AbstractImmutableIterator;
import com.nfsdb.collections.IntObjHashMap;
import com.nfsdb.utils.ByteBuffers;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TokenStream extends AbstractImmutableIterator<CharSequence> {
    private final IntObjHashMap<List<Token>> symbols = new IntObjHashMap<>();
    private final StringBuilder s = new StringBuilder();
    private ByteBuffer buffer;
    private CharSequence next = null;
    private int position;

    public void defineSymbol(String text) {
        defineSymbol(new Token(text));
    }

    public void defineSymbol(Token token) {
        char c0 = token.text.charAt(0);
        List<Token> l = symbols.get(c0);
        if (l == null) {
            l = new ArrayList<>();
            symbols.put(c0, l);
        }
        l.add(token);
        Collections.sort(l, new Comparator<Token>() {
            @Override
            public int compare(Token o1, Token o2) {
                return o2.text.length() - o1.text.length();
            }
        });
    }

    public Token getSymbol(char c) {

        List<Token> l = symbols.get(c);
        if (l == null) {
            return null;
        }

        int pos = buffer.position();
        for (int i = 0, sz = l.size(); i < sz; i++) {
            final Token t = l.get(i);
            boolean match = (t.text.length() - 2) < buffer.remaining();
            if (match) {
                for (int k = 1; k < t.text.length(); k++) {
                    if (buffer.getChar(pos + 2 * (k - 1)) != t.text.charAt(k)) {
                        match = false;
                        break;
                    }
                }
            }

            if (match) {
                return t;
            }
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        return next != null || (buffer != null && buffer.hasRemaining());
    }

    @Override
    public CharSequence next() {

        if (next != null) {
            CharSequence result = next;
            next = null;
            return result;
        }

        s.setLength(0);

        char term = 0;

        this.position = buffer.position();

        while (hasNext()) {
            char c = buffer.getChar();
            CharSequence token;
            switch (term) {
                case 1:
                    if ((token = token(c)) != null) {
                        return token;
                    } else {
                        s.append(c);
                    }
                    break;
                case 0:
                    switch (c) {
                        case '\'':
                            term = '\'';
                            break;
                        case '"':
                            term = '"';
                            break;
                        default:
                            if ((token = token(c)) != null) {
                                return token;
                            } else {
                                s.append(c);
                            }
                            term = 1;
                            break;
                    }
                    break;
                case '\'':
                    switch (c) {
                        case '\'':
                            return s;
                        default:
                            s.append(c);
                    }
                    break;
                case '"':
                    switch (c) {
                        case '"':
                            return s;
                        default:
                            s.append(c);
                    }
            }
        }
        return s;
    }

    public int position() {
        return position >> 1;
    }

    public void setContent(CharSequence cs) {
        if (cs == null) {
            return;
        }

        if (cs.length() == 0 && buffer != null) {
            buffer.limit(0);
            return;
        }

        if (buffer == null || buffer.capacity() < cs.length() * 2) {
            buffer = ByteBuffer.allocate(cs.length() * 2);
        } else {
            buffer.limit(cs.length() * 2);
        }
        buffer.rewind();
        ByteBuffers.putStr(buffer, cs);
        buffer.rewind();
    }

    private CharSequence token(char c) {
        Token t = getSymbol(c);
        if (t != null) {
            buffer.position(buffer.position() + (t.text.length() - 1) * 2);
            if (s.length() == 0) {
                return t.text;
            } else {
                next = t.text;
            }
            return s;
        } else {
            return null;
        }
    }
}
