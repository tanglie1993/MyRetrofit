package test;

import com.google.common.reflect.TypeToken;
import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static main.retrofit.CallAdapter.Factory.getParameterUpperBound;
import static main.retrofit.CallAdapter.Factory.getRawType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by pc on 2018/7/14.
 */
public class CallAdapterTest {

    @Test public void parameterizedTypeInvalidIndex() {
        ParameterizedType listOfString = (ParameterizedType) new TypeToken<List<String>>() {}.getType();
        try {
            getParameterUpperBound(-1, listOfString);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Index -1 not in range [0,1) for java.util.List<java.lang.String>");
        }
        try {
            getParameterUpperBound(1, listOfString);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("Index 1 not in range [0,1) for java.util.List<java.lang.String>");
        }
    }

    @Test public void parameterizedTypes() {
        ParameterizedType one = (ParameterizedType) new TypeToken<List<String>>() {}.getType();
        assertThat(getParameterUpperBound(0, one)).isSameAs(String.class);

        ParameterizedType two = (ParameterizedType) new TypeToken<Map<String, String>>() {}.getType();
        assertThat(getParameterUpperBound(0, two)).isSameAs(String.class);
        assertThat(getParameterUpperBound(1, two)).isSameAs(String.class);

        ParameterizedType wild = (ParameterizedType) new TypeToken<List<? extends CharSequence>>() {
        }.getType();
        assertThat(getParameterUpperBound(0, wild)).isSameAs(CharSequence.class);
    }

    @Test public void rawTypeThrowsOnNull() {
        try {
            getRawType(null);
            fail();
        } catch (NullPointerException e) {
            assertThat(e).hasMessage("type == null");
        }
    }

    @Test public void rawTypes() throws NoSuchMethodException {
        assertThat(getRawType(String.class)).isSameAs(String.class);

        Type listOfString = new TypeToken<List<String>>() {}.getType();
        assertThat(getRawType(listOfString)).isSameAs(List.class);

        Type stringArray = new TypeToken<String[]>() {}.getType();
        assertThat(getRawType(stringArray)).isSameAs(String[].class);

        Type wild = ((ParameterizedType) new TypeToken<List<? extends CharSequence>>() {
        }.getType()).getActualTypeArguments()[0];
        assertThat(getRawType(wild)).isSameAs(CharSequence.class);

        Type wildParam = ((ParameterizedType) new TypeToken<List<? extends List<String>>>() {
        }.getType()).getActualTypeArguments()[0];
        assertThat(getRawType(wildParam)).isSameAs(List.class);

        Type typeVar = A.class.getDeclaredMethod("method").getGenericReturnType();
        assertThat(getRawType(typeVar)).isSameAs(Object.class);
    }

    @SuppressWarnings("unused") // Used reflectively.
    static class A<T> {
        T method() {
            return null;
        }
    }
}
