package explicit;

import prism.Evaluator;

import java.util.Objects;

/**
 * An action-{@link Distribution} pair.
 */
public class ActionDistribution<Value> extends Distribution<Value>
{
    private Object action;

    public ActionDistribution(Evaluator<Value> eval)
    {
        super(eval);
        this.action = null;
    }

    public ActionDistribution(Distribution<Value> distr)
    {
        super(distr);
        this.action = null;
    }

    public ActionDistribution(Distribution<Value> distr, Object action)
    {
        super(distr);
        this.action = action;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ActionDistribution<?> that = (ActionDistribution<?>) o;
        return Objects.equals(action, that.action);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(super.hashCode(), action);
    }

    @Override
    public String toString()
    {
        return  (action == null ? "" : action.toString()) + ":" + super.toString();
    }
}
