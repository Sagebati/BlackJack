package agent;

import java.util.List;

public interface Intelligency<I> {
    I ai(List<I> moves);
}
