package org.cyclops.integrateddynamics.core.part.aspect.build;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.api.part.aspect.IAspectRead;
import org.cyclops.integrateddynamics.api.part.aspect.IAspectWrite;
import org.cyclops.integrateddynamics.api.part.aspect.property.IAspectProperties;
import org.cyclops.integrateddynamics.api.part.write.IPartStateWriter;
import org.cyclops.integrateddynamics.api.part.write.IPartTypeWriter;
import org.cyclops.integrateddynamics.core.helper.Helpers;
import org.cyclops.integrateddynamics.part.aspect.read.AspectReadBase;
import org.cyclops.integrateddynamics.part.aspect.write.AspectWriteBase;

import java.util.Collections;
import java.util.List;

/**
 * Immutable builder for aspects.
 * @param <V> The value type.
 * @param <T> The value type type.
 * @param <O> The current output type for value handling.
 * @author rubensworks
 */
public class AspectBuilder<V extends IValue, T extends IValueType<V>, O> {

    private final boolean read;
    private final T valueType;
    private final List<String> kinds;
    private final IAspectProperties defaultAspectProperties;
    private final List<IAspectValuePropagator> valuePropagators;
    private final List<IAspectWriteActivator> writeActivators;
    private final List<IAspectWriteDeactivator> writeDeactivators;

    private AspectBuilder(boolean read, T valueType, List<String> kinds, IAspectProperties defaultAspectProperties,
                          List<IAspectValuePropagator> valuePropagators, List<IAspectWriteActivator> writeActivators,
                          List<IAspectWriteDeactivator> writeDeactivators) {
        this.read = read;
        this.valueType = valueType;
        this.kinds = kinds;
        this.defaultAspectProperties = defaultAspectProperties;
        this.valuePropagators = valuePropagators;
        this.writeActivators = writeActivators;
        this.writeDeactivators = writeDeactivators;
    }

    /**
     * Add the given value propagator.
     * @param valuePropagator The value propagator.
     * @param <O2> The new output type.
     * @return The new builder instance.
     */
    public <O2> AspectBuilder<V, T, O2> handle(IAspectValuePropagator<O, O2> valuePropagator) {
        return handle(valuePropagator, null);
    }

    /**
     * Add the given value propagator.
     * @param valuePropagator The value propagator.
     * @param kind The kind to append.
     * @param <O2> The new output type.
     * @return The new builder instance.
     */
    public <O2> AspectBuilder<V, T, O2> handle(IAspectValuePropagator<O, O2> valuePropagator, String kind) {
        return new AspectBuilder<>(
                this.read, this.valueType,
                Helpers.joinList(this.kinds, kind),
                this.defaultAspectProperties,
                Helpers.joinList(this.valuePropagators, valuePropagator),
                Helpers.joinList(writeActivators, null),
                Helpers.joinList(writeDeactivators, null));
    }

    /**
     * Add the given kind.
     * @param kind The kind to append.
     * @return The new builder instance.
     */
    public AspectBuilder<V, T, O> appendKind(String kind) {
        return new AspectBuilder<>(
                this.read, this.valueType,
                Helpers.joinList(this.kinds, kind),
                this.defaultAspectProperties,
                Helpers.joinList(this.valuePropagators, null),
                Helpers.joinList(writeActivators, null),
                Helpers.joinList(writeDeactivators, null));
    }

    /**
     * Set the given default aspect properties.
     * @param aspectProperties The aspect properties.
     * @return The new builder instance.
     */
    public AspectBuilder<V, T, O> withProperties(IAspectProperties aspectProperties) {
        return new AspectBuilder<>(
                this.read, this.valueType,
                Helpers.joinList(this.kinds, null),
                aspectProperties,
                Helpers.joinList(this.valuePropagators, null),
                Helpers.joinList(writeActivators, null),
                Helpers.joinList(writeDeactivators, null));
    }

    /**
     * Add the given aspect activator.
     * Only applicable for writers.
     * @param activator The aspect activator callback.
     * @return The new builder instance.
     */
    public AspectBuilder<V, T, O> appendActivator(IAspectWriteActivator activator) {
        if(this.read) {
            throw new RuntimeException("Activators are only applicable for writers.");
        }
        return new AspectBuilder<>(
                this.read, this.valueType,
                Helpers.joinList(this.kinds, null),
                this.defaultAspectProperties,
                Helpers.joinList(this.valuePropagators, null),
                Helpers.joinList(writeActivators, activator),
                Helpers.joinList(writeDeactivators, null));
    }

    /**
     * Add the given aspect deactivator.
     * Only applicable for writers.
     * @param deactivator The aspect deactivator callback.
     * @return The new builder instance.
     */
    public AspectBuilder<V, T, O> appendDeactivator(IAspectWriteDeactivator deactivator) {
        if(this.read) {
            throw new RuntimeException("Deactivators are only applicable for writers.");
        }
        return new AspectBuilder<>(
                this.read, this.valueType,
                Helpers.joinList(this.kinds, null),
                this.defaultAspectProperties,
                Helpers.joinList(this.valuePropagators, null),
                Helpers.joinList(writeActivators, null),
                Helpers.joinList(writeDeactivators, deactivator));
    }

    /**
     * @return The built read aspect.
     */
    public IAspectRead<V, T> buildRead() {
        if(!this.read) {
            throw new RuntimeException("Tried to build a reader from a writer builder");
        }
        return new BuiltReader<V, T>((AspectBuilder<V, T, V>) this);
    }

    /**
     * @return The built write aspect.
     */
    public IAspectWrite<V, T> buildWrite() {
        if(this.read) {
            throw new RuntimeException("Tried to build a writer from a reader builder");
        }
        return new BuiltWriter<V, T>((AspectBuilder<V, T, V>) this);
    }

    /**
     * Create a new read builder for the given value type.
     * @param valueType The value type the eventual built aspect will output.
     * @param <V> The value type.
     * @param <T> The value type type.
     * @return The builder instance.
     */
    public static <V extends IValue, T extends IValueType<V>> AspectBuilder<V, T, Pair<PartTarget, IAspectProperties>> forReadType(T valueType) {
        return new AspectBuilder<>(true, valueType, Lists.newArrayList(valueType.getTypeName()), null,
                Collections.<IAspectValuePropagator>emptyList(), Collections.<IAspectWriteActivator>emptyList(),
                Collections.<IAspectWriteDeactivator>emptyList());
    }

    /**
     * Create a new write builder for the given value type.
     * @param valueType The value type the eventual built aspect expects.
     * @param <V> The value type.
     * @param <T> The value type type.
     * @return The builder instance.
     */
    public static <V extends IValue, T extends IValueType<V>> AspectBuilder<V, T, Triple<PartTarget, IAspectProperties, IVariable<V>>> forWriteType(T valueType) {
        return new AspectBuilder<>(false, valueType, Lists.newArrayList(valueType.getTypeName()), null,
                Collections.<IAspectValuePropagator>emptyList(), Collections.<IAspectWriteActivator>emptyList(),
                Collections.<IAspectWriteDeactivator>emptyList());
    }

    private static class BuiltReader<V extends IValue, T extends IValueType<V>> extends AspectReadBase<V, T> {

        private final T valueType;
        private final List<IAspectValuePropagator> valuePropagators;

        public BuiltReader(AspectBuilder<V, T, V> aspectBuilder) {
            super(deriveUnlocalizedType(aspectBuilder), aspectBuilder.defaultAspectProperties);
            this.valueType = aspectBuilder.valueType;
            this.valuePropagators = aspectBuilder.valuePropagators;
        }

        protected static <V extends IValue, T extends IValueType<V>> String deriveUnlocalizedType(AspectBuilder<V, T, V> aspectBuilder) {
            StringBuilder sb = new StringBuilder();
            for(String kind : aspectBuilder.kinds) {
                sb.append(".");
                sb.append(kind);
            }
            return sb.toString();
        }

        @Override
        protected V getValue(PartTarget target, IAspectProperties properties) {
            Object output = Pair.of(target, properties);
            for(IAspectValuePropagator valuePropagator : valuePropagators) {
                try {
                    output = valuePropagator.getOutput(output);
                } catch (EvaluationException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Caught unexpected exception in read aspect, this is probably a programming error.");
                }
            }
            return (V) output;
        }

        @Override
        public T getValueType() {
            return valueType;
        }
    }

    private static class BuiltWriter<V extends IValue, T extends IValueType<V>> extends AspectWriteBase<V, T> {

        private final T valueType;
        private final List<IAspectValuePropagator> valuePropagators;
        private final List<IAspectWriteActivator> writeActivators;
        private final List<IAspectWriteDeactivator> writeDeactivators;

        public BuiltWriter(AspectBuilder<V, T, V> aspectBuilder) {
            super(deriveUnlocalizedType(aspectBuilder), aspectBuilder.defaultAspectProperties);
            this.valueType = aspectBuilder.valueType;
            this.valuePropagators = aspectBuilder.valuePropagators;
            this.writeActivators = aspectBuilder.writeActivators;
            this.writeDeactivators = aspectBuilder.writeDeactivators;
        }

        protected static <V extends IValue, T extends IValueType<V>> String deriveUnlocalizedType(AspectBuilder<V, T, V> aspectBuilder) {
            StringBuilder sb = new StringBuilder();
            for(String kind : aspectBuilder.kinds) {
                sb.append(".");
                sb.append(kind);
            }
            return sb.toString();
        }

        @Override
        public T getValueType() {
            return valueType;
        }

        @Override
        public <P extends IPartTypeWriter<P, S>, S extends IPartStateWriter<P>> void write(P partType, PartTarget target, S state, IVariable<V> variable) throws EvaluationException {
            IAspectProperties properties = hasProperties() ? getProperties(partType, target, state) : null;
            Object output = Triple.of(target, properties, variable);
            for(IAspectValuePropagator valuePropagator : valuePropagators) {
                output = valuePropagator.getOutput(output);
            }
        }

        @Override
        public <P extends IPartTypeWriter<P, S>, S extends IPartStateWriter<P>> void onActivate(P partType, PartTarget target, S state) {
            super.onActivate(partType, target, state);
            for (IAspectWriteActivator writeActivator : this.writeActivators) {
                writeActivator.onActivate(partType, target, state);
            }
        }

        @Override
        public <P extends IPartTypeWriter<P, S>, S extends IPartStateWriter<P>> void onDeactivate(P partType, PartTarget target, S state) {
            super.onDeactivate(partType, target, state);
            for (IAspectWriteDeactivator writeDeactivator : this.writeDeactivators) {
                writeDeactivator.onDeactivate(partType, target, state);
            }

        }
    }

}