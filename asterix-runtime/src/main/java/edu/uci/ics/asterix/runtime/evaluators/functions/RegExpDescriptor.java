package edu.uci.ics.asterix.runtime.evaluators.functions;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.ics.asterix.common.functions.FunctionConstants;
import edu.uci.ics.asterix.common.utils.UTF8CharSequence;
import edu.uci.ics.asterix.formats.nontagged.AqlBinaryComparatorFactoryProvider;
import edu.uci.ics.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import edu.uci.ics.asterix.om.base.ABoolean;
import edu.uci.ics.asterix.om.base.ANull;
import edu.uci.ics.asterix.om.base.AString;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptor;
import edu.uci.ics.asterix.om.functions.IFunctionDescriptorFactory;
import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.asterix.om.types.BuiltinType;
import edu.uci.ics.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.ICopyEvaluatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ByteArrayAccessibleOutputStream;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

/**
 * Creates new Matcher and Pattern objects each time the value of the pattern
 * argument (the second argument) changes.
 */

public class RegExpDescriptor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private final static FunctionIdentifier FID = new FunctionIdentifier(FunctionConstants.ASTERIX_NS, "reg-exp", 2);
    private final static byte SER_NULL_TYPE_TAG = ATypeTag.NULL.serialize();
    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {
        public IFunctionDescriptor createFunctionDescriptor() {
            return new RegExpDescriptor();
        }
    };

    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

    @Override
    public ICopyEvaluatorFactory createEvaluatorFactory(final ICopyEvaluatorFactory[] args) throws AlgebricksException {

        return new ICopyEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public ICopyEvaluator createEvaluator(IDataOutputProvider output) throws AlgebricksException {

                final DataOutput dout = output.getDataOutput();

                return new ICopyEvaluator() {

                    private boolean first = true;
                    private ArrayBackedValueStorage array0 = new ArrayBackedValueStorage();
                    private ICopyEvaluator evalString = args[0].createEvaluator(array0);
                    private ICopyEvaluator evalPattern = args[1].createEvaluator(array0);
                    private ByteArrayAccessibleOutputStream lastPattern = new ByteArrayAccessibleOutputStream();
                    private UTF8CharSequence carSeq = new UTF8CharSequence();
                    private IBinaryComparator strComp = AqlBinaryComparatorFactoryProvider.INSTANCE
                            .getBinaryComparatorFactory(BuiltinType.ASTRING, true).createBinaryComparator();
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<AString> stringSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ASTRING);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ABoolean> booleanSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ABOOLEAN);
                    @SuppressWarnings("unchecked")
                    private ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ANULL);
                    private Matcher matcher;
                    private Pattern pattern;

                    @Override
                    public void evaluate(IFrameTupleReference tuple) throws AlgebricksException {
                        // evaluate the pattern first
                        try {
                            array0.reset();
                            evalPattern.evaluate(tuple);
                            if (array0.getByteArray()[0] == SER_NULL_TYPE_TAG) {
                                nullSerde.serialize(ANull.NULL, dout);
                                return;
                            }
                            boolean newPattern = false;
                            if (first) {
                                first = false;
                                newPattern = true;
                            } else {
                                int c = strComp.compare(array0.getByteArray(), array0.getStartOffset(),
                                        array0.getLength(), lastPattern.getByteArray(), 0, lastPattern.size());
                                if (c != 0) {
                                    newPattern = true;
                                }
                            }
                            if (newPattern) {
                                lastPattern.reset();
                                lastPattern.write(array0.getByteArray(), array0.getStartOffset(), array0.getLength());
                                // ! object creation !
                                DataInputStream di = new DataInputStream(new ByteArrayInputStream(
                                        lastPattern.getByteArray()));
                                AString strPattern = (AString) stringSerde.deserialize(di);
                                pattern = Pattern.compile(strPattern.getStringValue());

                            }
                            array0.reset();
                            evalString.evaluate(tuple);
                            if (array0.getByteArray()[0] == SER_NULL_TYPE_TAG) {
                                nullSerde.serialize(ANull.NULL, dout);
                                return;
                            }
                            carSeq.reset(array0, 1);
                            if (newPattern) {
                                matcher = pattern.matcher(carSeq);
                            } else {
                                matcher.reset(carSeq);
                            }
                            ABoolean res = (matcher.find(0)) ? ABoolean.TRUE : ABoolean.FALSE;
                            booleanSerde.serialize(res, dout);
                        } catch (HyracksDataException e) {
                            throw new AlgebricksException(e);
                        }
                    }
                };
            }
        };
    }
}