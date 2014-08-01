package kikaha.urouting.api;

import java.io.Writer;

public interface Serializer {

	<T> void serialize( T object, Writer output ) throws RoutingException;
}