package au.net.causal.maven.plugins.autojdk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A comparator that will order values based on an existing list of values, optionally comparing unknown values using a wildcard placeholder element in the
 * order preference list or just placing unknown values at the bottom if none is specified.
 * <p>
 *
 * For example, if we have a list where fruits should be placed at the top of the list, we can supply a known ordered values preference list of ['apple', 'orange', 'banana']
 * when constructing this comparator.  Then, when sorting lists using this comparator, any 'apple', 'orange' or 'banana' elements will be sorted at the top of the list in that
 * order, followed by the other unknown elements.  To place fruits always at the end of the list instead, use the wildcard placeholder first, so a preference list of
 * [null, 'apple', 'orange', 'banana'] with null as the placeholder will sort everything else before these fruits.
 *
 * @param <T> types of objects to compare.
 */
public class KnownValueComparator<T> implements Comparator<T>
{
    private final Map<T, Integer> knownValuePositionMap;
    private final int wildcardPosition;

    /**
     * Constructs a known value comparator with an unused or null wildcard placeholder.
     *
     * @param knownOrderedValues known values used for ordering.
     */
    public KnownValueComparator(List<T> knownOrderedValues)
    {
        this(knownOrderedValues, null);
    }

    /**
     * Constructs a known value comparator.
     *
     * @param knownOrderedValues known values used for ordering.  If the wildcard placeholder value is not in this list, it is assumed unknown elements will be placed at the
     *                           end when sorting/comparing.
     * @param wildcardPlaceholder wildcard placeholder values where all unknown values will be sorted.  If this is unused, it can be null.
     */
    public KnownValueComparator(List<T> knownOrderedValues, T wildcardPlaceholder)
    {
        Map<T, Integer> knownValuePositionMap = new HashMap<>();
        int wildcardPosition = -1;

        //Build the known position map
        int index = 0;
        for (T knownOrderedValue : knownOrderedValues)
        {
            if (wildcardPosition < 0 && Objects.equals(knownOrderedValue, wildcardPlaceholder))
                wildcardPosition = index;
            else
                knownValuePositionMap.putIfAbsent(knownOrderedValue, index);

            index++;
        }

        //If no wildcard explicitly defined then put all remaining values at the end
        if (wildcardPosition < 0)
            wildcardPosition = knownOrderedValues.size();

        this.wildcardPosition = wildcardPosition;
        this.knownValuePositionMap = Map.copyOf(knownValuePositionMap);
    }

    @Override
    public int compare(T o1, T o2)
    {
        Integer pos1 = knownValuePositionMap.get(o1);
        Integer pos2 = knownValuePositionMap.get(o2);

        if (pos1 == null)
            pos1 = wildcardPosition;
        if (pos2 == null)
            pos2 = wildcardPosition;

        return Integer.compare(pos1, pos2);
    }
}
