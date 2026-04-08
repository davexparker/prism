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

    public ActionDistribution(Evaluator<Value> eval, Object action)
    {
        super(eval);
        this.action = action;
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

    /**
     * Construct an action-distribution pair with an empty distribution
     * assuming an Evaluator of type Double.
     */
    public static ActionDistribution<Double> ofDouble(Object action)
    {
        return new ActionDistribution<>(Evaluator.forDouble(), action);
    }

    public Object getAction()
    {
        return action;
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
