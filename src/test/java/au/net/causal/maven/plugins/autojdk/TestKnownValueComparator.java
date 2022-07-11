package au.net.causal.maven.plugins.autojdk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TestKnownValueComparator
{
    @Test
    void noExplicitWildcard()
    {
        List<String> preferenceList = List.of("apple", "pineapple", "pear");
        KnownValueComparator<String> comparator = new KnownValueComparator<>(preferenceList);

        List<String> values = Arrays.asList("orange", "apple", "banana", "pear");
        values.sort(comparator);

        //Apple/pear because of preferences, the rest keep their original order
        assertThat(values).containsExactly("apple", "pear", "orange", "banana");
    }

    @Test
    void explicitWildcardAtEnd()
    {
        List<String> preferenceList = List.of("apple", "pineapple", "pear", "*");
        KnownValueComparator<String> comparator = new KnownValueComparator<>(preferenceList, "*");

        List<String> values = Arrays.asList("orange", "apple", "banana", "pear");
        values.sort(comparator);

        //Apple/pear because of preferences, the rest keep their original order
        assertThat(values).containsExactly("apple", "pear", "orange", "banana");
    }

    @Test
    void explicitWildcardAtStart()
    {
        List<String> preferenceList = List.of("*", "apple", "pineapple", "pear");
        KnownValueComparator<String> comparator = new KnownValueComparator<>(preferenceList, "*");

        List<String> values = Arrays.asList("orange", "apple", "banana", "pear");
        values.sort(comparator);

        //Apple/pear because of preferences, the rest keep their original order
        assertThat(values).containsExactly("orange", "banana", "apple", "pear");
    }

    @Test
    void duplicateValues()
    {
        List<String> preferenceList = List.of("apple", "pineapple", "pear");
        KnownValueComparator<String> comparator = new KnownValueComparator<>(preferenceList);

        List<String> values = Arrays.asList("orange", "apple", "apple", "banana", "apple", "pear", "apple");
        values.sort(comparator);

        //Apple/pear because of preferences, the rest keep their original order
        assertThat(values).containsExactly("apple", "apple", "apple", "apple", "pear", "orange", "banana");
    }
}
