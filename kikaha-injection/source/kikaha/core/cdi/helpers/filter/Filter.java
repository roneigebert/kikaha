package kikaha.core.cdi.helpers.filter;

import kikaha.core.cdi.helpers.TinyList;
import lombok.val;

@SuppressWarnings({"unchecked", "rawtypes"})
public class Filter {

	public static <T> Iterable<T> filter( Iterable<T> self, Condition condition ) {
		val list = new TinyList<T>();
		for ( T object : self )
			if ( condition.check(object) )
				list.add(object);
		return list;
	}
	
	public static <T> T first( Iterable<T> self, Condition condition ) {
		for ( T object : self )
			if ( condition.check(object) )
				return object;
		return null;
	}
}
