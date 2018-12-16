/*
 * Copyright (C) 2014-2019 Luciano Vernaschi (luciano at cromoteca.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cromoteca.bfts.storage;

import com.cromoteca.bfts.util.Compression;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.regex.Pattern;

/**
 * Serializes objects using JSON and compresses them.&nbsp;Byte arrays are
 * treated as already serialized.
 *
 * @author Luciano Vernaschi (luciano at cromoteca.com)
 */
public class Serialization {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    MAPPER.registerModule(new SimpleModule() {
      {
        addDeserializer(Pattern.class, new PatternDeserializer());
        addSerializer(Pattern.class, new PatternSerializer());
      }
    });
  }

  private static class PatternDeserializer extends StdDeserializer<Pattern> {
    public PatternDeserializer() {
      super(Pattern.class);
    }

    @Override
    public Pattern deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      JsonNode node = p.getCodec().readTree(p);
      String regex = node.get("pattern").asText();
      int flags = (Integer) ((IntNode) node.get("flags")).numberValue();
      return Pattern.compile(regex, flags);
    }
  }

  private static class PatternSerializer extends StdSerializer<Pattern> {
    public PatternSerializer() {
      super(Pattern.class);
    }

    @Override
    public void serialize(Pattern value, JsonGenerator gen,
        SerializerProvider provider) throws IOException {
      gen.writeStartObject();
      gen.writeStringField("pattern", value.pattern());
      gen.writeNumberField("flags", value.flags());
      gen.writeEndObject();
    }
  }

  public static byte[] serialize(Object o) throws IOException {
    byte[] data;

    if (o == null) {
      data = new byte[0];
    } else if (o instanceof byte[]) {
      data = (byte[]) o;
    } else {
      data = MAPPER.writeValueAsBytes(o);
      data = Compression.compress(data);
    }

    return data;
  }

  public static <T> T deserialize(Type type, byte[] data) throws IOException {
    T result;

    if (data.length == 0) {
      result = null;
    } else if ("byte[]".equals(type.getTypeName())) {
      result = (T) data;
    } else {
      data = Compression.decompress(data);
      result = MAPPER.readValue(data, MAPPER.getTypeFactory().constructType(type));
    }

    return result;
  }
}
