package au.net.causal.maven.plugins.autojdk;

public interface ExceptionalSupplier<T, E extends Exception>
{
	public T get()
	throws E;
}
