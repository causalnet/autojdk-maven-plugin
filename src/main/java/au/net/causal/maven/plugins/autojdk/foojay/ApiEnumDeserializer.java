package au.net.causal.maven.plugins.autojdk.foojay;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import eu.hansolo.jdktools.Api;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApiEnumDeserializer<E extends Enum<E> & Api> extends StdDeserializer<E> implements ContextualDeserializer
{
    private final Function<String, E> fromTextFunction;

    private final BeanProperty property;
    private final Class<E> enumType;

    public ApiEnumDeserializer(Function<String, E> fromTextFunction)
    {
        super(Api.class);
        this.property = null;
        this.enumType = null;
        this.fromTextFunction = fromTextFunction;
    }

    public ApiEnumDeserializer(Function<String, E> fromTextFunction, BeanProperty property, Class<E> enumType)
    {
        super(enumType);

        if (fromTextFunction == null)
        {
            //Make one from the API strings if nothing was supplied
            Map<String, E> enumApiStringMap = Stream.of(enumType.getEnumConstants()).collect(Collectors.toUnmodifiableMap(
                                                    Api::getApiString,
                                                    Function.identity(),
                                                    (e, e2) -> e //If there are duplicate API strings, just use the first one and don't break
            ));
            fromTextFunction = enumApiStringMap::get;
        }

        this.fromTextFunction = fromTextFunction;
        this.property = property;
        this.enumType = enumType;
    }

    @Override
    public E deserialize(JsonParser p, DeserializationContext ctxt)
    throws IOException, JacksonException
    {
        if (enumType == null)
            throw new IOException("No context for deserializing API enum.");

        String rawValue = ctxt.readPropertyValue(p, property, String.class);
        if (rawValue == null || rawValue.isEmpty())
            return null;

        return fromTextFunction.apply(rawValue);
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
    throws JsonMappingException
    {
        JavaType propertyType = property.getType();
        if (!propertyType.isEnumImplType())
            throw JsonMappingException.from(ctxt, "Attempt to use API enum deserializer on non enum property " + property.getType());
        if (!propertyType.isTypeOrSubTypeOf(Api.class))
            throw JsonMappingException.from(ctxt, "Attempt to use API enum deserializer on non API property " + property.getType());

        @SuppressWarnings({"unchecked", "rawtypes"}) //We have checked this above
        ApiEnumDeserializer<?> contextual = new ApiEnumDeserializer<>(fromTextFunction, property, (Class)propertyType.getRawClass());

        return contextual;
    }
}
