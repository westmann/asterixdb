/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.asterix.experiment.builder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.asterix.experiment.action.base.AbstractAction;
import org.apache.asterix.experiment.action.base.ParallelActionSet;
import org.apache.asterix.experiment.action.base.SequentialActionList;
import org.apache.asterix.experiment.action.derived.AbstractRemoteExecutableAction;
import org.apache.asterix.experiment.action.derived.RunAQLStringAction;
import org.apache.asterix.experiment.action.derived.RunRESTIOWaitAction;
import org.apache.asterix.experiment.action.derived.SleepAction;
import org.apache.asterix.experiment.action.derived.TimedAction;
import org.apache.asterix.experiment.client.LSMExperimentConstants;
import org.apache.asterix.experiment.client.LSMExperimentSetRunner.LSMExperimentSetRunnerConfig;
import org.apache.asterix.experiment.client.OrchestratorServer;
import org.apache.commons.lang3.StringUtils;

public abstract class AbstractExperiment8Builder extends AbstractLSMBaseExperimentBuilder {

    private static final long DOMAIN_SIZE = (1L << 32);

    private static final long EXPECTED_RANGE_CARDINALITY = 50000;

    private static int N_PARTITIONS = 16;

    private final int nIntervals;

    private final String orchHost;

    private final int orchPort;

    protected final long dataInterval;

    protected final int nQueryRuns;

    protected final Random randGen;

    private final String pointQueryTemplate;
    private final String rangeQueryTemplate;

    public AbstractExperiment8Builder(String name, LSMExperimentSetRunnerConfig config, String clusterConfigFileName,
            String ingestFileName, String dgenFileName) throws IOException {
        super(name, config, clusterConfigFileName, ingestFileName, dgenFileName, null);
        nIntervals = config.getNIntervals();
        orchHost = config.getOrchestratorHost();
        orchPort = config.getOrchestratorPort();
        dataInterval = config.getDataInterval();
        this.nQueryRuns = config.getNQueryRuns();
        this.randGen = new Random();
        this.pointQueryTemplate = getPointQueryTemplate();
        this.rangeQueryTemplate = getRangeQueryTemplate();
    }

    protected void doBuildProtocolAction(SequentialActionList seq, int round) throws Exception {
        for (int i = 0; i < nQueryRuns; ++i) {
            //            String aql = getPointLookUpAQL(round);
            //            seq.add(new TimedAction(new RunAQLStringAction(httpClient, restHost, restPort, aql)));
            String aql = getRangeAQL(round);
            seq.add(new TimedAction(new RunAQLStringAction(httpClient, restHost, restPort, aql)));
        }
    }

    @Override
    protected void doBuildDataGen(SequentialActionList seq, Map<String, List<String>> dgenPairs) throws Exception {
        //start datagen
        SequentialActionList[] protocolActions = new SequentialActionList[nIntervals];
        for (int i = 0; i < nIntervals; i++) {
            protocolActions[i] = new SequentialActionList();
            protocolActions[i].add(new SleepAction(10000));
            protocolActions[i].add(new RunRESTIOWaitAction(httpClient, restHost, restPort));
            protocolActions[i].add(new SleepAction(10000));
            doBuildProtocolAction(protocolActions[i], i);
        }

        int nDgens = 0;
        for (List<String> v : dgenPairs.values()) {
            nDgens += v.size();
        }
        final OrchestratorServer oServer = new OrchestratorServer(orchPort, nDgens, nIntervals, protocolActions);

        seq.add(new AbstractAction() {

            @Override
            protected void doPerform() throws Exception {
                oServer.start();
            }
        });

        ParallelActionSet dgenActions = new ParallelActionSet();
        int partition = 0;

        // run dgen
        for (String dgenHost : dgenPairs.keySet()) {
            final List<String> rcvrs = dgenPairs.get(dgenHost);
            final int p = partition;
            dgenActions.add(new AbstractRemoteExecutableAction(dgenHost, username, sshKeyLocation) {

                @Override
                protected String getCommand() {
                    String ipPortPairs = StringUtils.join(rcvrs.iterator(), " ");
                    String binary = "JAVA_HOME=" + javaHomePath + " "
                            + localExperimentRoot.resolve("bin").resolve("datagenrunner").toString();
                    return StringUtils.join(new String[] { binary, "-si", "" + locationSampleInterval, "-of",
                            openStreetMapFilePath, "-p", "" + p, "-di", "" + dataInterval, "-ni", "" + nIntervals,
                            "-oh", orchHost, "-op", "" + orchPort, ipPortPairs }, " ");
                }
            });
            partition += rcvrs.size();
        }
        seq.add(dgenActions);

        // wait until all dgen / queries are done
        seq.add(new AbstractAction() {

            @Override
            protected void doPerform() throws Exception {
                oServer.awaitFinished();
            }
        });
    }

    private String getRangeQueryTemplate() throws IOException {
        Path aqlTemplateFilePath = localExperimentRoot.resolve(LSMExperimentConstants.AQL_DIR).resolve("8_q2.aql");
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(Files.readAllBytes(aqlTemplateFilePath))).toString();
    }

    private String getPointQueryTemplate() throws IOException {
        Path aqlTemplateFilePath = localExperimentRoot.resolve(LSMExperimentConstants.AQL_DIR).resolve("8_q1.aql");
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(Files.readAllBytes(aqlTemplateFilePath))).toString();
    }

    protected String getPointLookUpAQL(int round) {
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.put((byte) 0);
        bb.put((byte) randGen.nextInt(N_PARTITIONS));
        bb.putShort((short) 0);
        bb.putInt(randGen.nextInt((int) (((1 + round) * dataInterval) / 1000)));
        bb.flip();
        long key = bb.getLong();
        return pointQueryTemplate.replaceAll("\\$KEY\\$", Long.toString(key));
    }

    protected String getRangeAQL(int round) {
        long numKeys = (((1 + round) * dataInterval) / 1000) * N_PARTITIONS;
        long rangeSize = (long) ((EXPECTED_RANGE_CARDINALITY / (double) numKeys) * DOMAIN_SIZE);
        int lowKey = randGen.nextInt();
        long maxLowKey = Integer.MAX_VALUE - rangeSize;
        if (lowKey > maxLowKey) {
            lowKey = (int) maxLowKey;
        }
        int highKey = (int) (lowKey + rangeSize);
        return rangeQueryTemplate.replaceAll("\\$LKEY\\$", Long.toString(lowKey)).replaceAll("\\$HKEY\\$",
                Long.toString(highKey));
    }
}
