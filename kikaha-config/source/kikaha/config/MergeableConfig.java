package kikaha.config;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 *
 */
@RequiredArgsConstructor
public class MergeableConfig implements Config {

	final Yaml yaml = new Yaml();
	final Map<String, Object> conf;
	final String rootPath;

	public static MergeableConfig create(){
		return new MergeableConfig( new HashMap<>(), "" );
	}

	public MergeableConfig load(File file ) throws IOException {
		try (final FileInputStream inputStream = new FileInputStream(file) ) {
			return load( inputStream );
		}
	}

	public MergeableConfig load(InputStream inputStream){
		Map<String, Object> newConf = (Map<String, Object>) yaml.load(inputStream);
		deepMerge( conf, newConf );
		return this;
	}

	@Override
	public String toString() {
		return conf.toString();
	}

	@Override
	public Map<String, Object> toMap(){
		return conf;
	}

	@Override
	public Set<String> getKeys() {
		return conf.keySet();
	}

	@Override
	public Config getConfig(String path) {
		final Map<String, Object> current = read(path, o->(Map<String,Object>)o);
		if ( current == null )
			return null;
		return new MergeableConfig(current, rootPath + path + ".");
	}

	@Override
	public Object getObject(String path) {
		return read( path, o->o );
	}

	public Class<?> getClass( String path ) {
		final String clazzName = getString(path);
		if ( clazzName == null )
			return null;
		return instantiate( clazzName );
	}

	private Class<?> instantiate( String className ) {
		try {
			return (Class<?>) Class.forName( className );
		} catch ( Throwable cause ) {
			throw new IllegalStateException( "Can't load " + className, cause);
		}
	}

	@Override
	public String getString(String path, String defaultValue) {
		final String value = getString(path);
		return value != null ? value : defaultValue;
	}

	@Override
	public String getString(String path) {
		final String propertyValue = System.getProperty(rootPath + path);
		if ( propertyValue != null )
			return propertyValue;
		return read( path, o->(String)o );
	}

	@Override
	public Boolean getBoolean(String path) {
		final String propertyValue = System.getProperty(rootPath + path);
		if ( propertyValue != null )
			return Boolean.valueOf(propertyValue);
		return read( path, o->(Boolean)o );
	}

	@Override
	public Integer getInteger(String path) {
		final String propertyValue = System.getProperty(rootPath + path);
		if ( propertyValue != null )
			return Integer.valueOf( propertyValue );
		return read( path, o->(Integer)o );
	}

	@Override
	public List<Config> getConfigList(String path){
		final List<Map<String, Object>> current = read(path, o->(List<Map<String,Object>>)o);
		if ( current == null )
			return Collections.emptyList();
		return current.stream().map( m -> new MergeableConfig(m, rootPath + path + ".") ).collect(Collectors.toList());
	}

	@Override
	public List<String> getStringList(String path) {
		final List<String> current = read(path, o -> (List<String>) o);
		if ( current == null )
			return Collections.emptyList();
		return current;
	}

	private <T> T read(String path, Function<Object,T> parser ) {
		final String[] strings = path.split("\\.");
		Map<String, Object> current = readPath(strings);
		if ( current == null )
			current = conf;
		final Object last = current.get( strings[strings.length-1] );
		return parser.apply( last );
	}

	private Map<String, Object> readPath( String[] strings ){
		Map<String, Object> current = conf;
		for ( int i=0; i<strings.length-1 && current != null; i++ )
			current = (Map<String, Object>) current.get( strings[i] );
		return current;
	}

	/**
	 * Apply a deep merge on two {@code Map}s. Thanks to
	 * {@code http://stackoverflow.com/a/36123154/548685}.
	 *
	 * @param original
	 * @param newMap
	 */
	private static void deepMerge( Map original, Map newMap) {
		for (Entry e : (Set<Entry>) newMap.entrySet()) {
			Object currentKey = e.getKey(), currentValue = e.getValue();
			if (shouldMergeAnyWay( original, currentKey, currentValue ))
				original.put(currentKey, currentValue);
		}
	}

	private static boolean shouldMergeAnyWay(Map original, Object currentKey, Object currentValue ){
		if (original.containsKey(currentKey)) {
			Object originalValue = original.get(currentKey);

			if (originalValue instanceof Collection) {
				checkArgument(currentValue instanceof Collection,
						"A non-collection collided with a collection: %s\t%s",currentValue, originalValue);
				((Collection) originalValue).addAll((Collection) currentValue);
				return false;
			}

			if (originalValue instanceof Map) {
				checkArgument(currentValue instanceof Map,
						"A non-map collided with a map: %s\t%s",
						currentValue, originalValue);
				deepMerge((Map) originalValue, (Map) currentValue);
				return false;
			}
		}

		return true;
	}

	private static void checkArgument( boolean isValid, String str, Object...args ){
		if ( !isValid )
			throw new IllegalArgumentException( format(str, args) );
	}
}
