package edu.uci.ics.hivesterix.test.datagen;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;

@SuppressWarnings("deprecation")
public class RecordBalance {

	private static String confPath = System.getenv("HADDOP_HOME");
	private static Path[] inputPaths = { new Path("/tpch/100x/customer"),
			new Path("/tpch/100x/nation"), new Path("/tpch/100x/region"),
			new Path("/tpch/100x/lineitem"), new Path("/tpch/100x/orders"),
			new Path("/tpch/100x/part"), new Path("/tpch/100x/partsupp"),
			new Path("/tpch/100x/supplier") };

	private static Path[] outputPaths = { new Path("/tpch/100/customer"),
			new Path("/tpch/100/nation"), new Path("/tpch/100/region"),
			new Path("/tpch/100/lineitem"), new Path("/tpch/100/orders"),
			new Path("/tpch/100/part"), new Path("/tpch/100/partsupp"),
			new Path("/tpch/100/supplier") };

	public static class MapRecordOnly extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {

		public void map(LongWritable id, Text inputValue,
				OutputCollector<LongWritable, Text> output, Reporter reporter)
				throws IOException {
			output.collect(id, inputValue);
		}
	}

	public static class ReduceRecordOnly extends MapReduceBase implements
			Reducer<LongWritable, Text, NullWritable, Text> {

		NullWritable key = NullWritable.get();

		public void reduce(LongWritable inputKey, Iterator<Text> inputValue,
				OutputCollector<NullWritable, Text> output, Reporter reporter)
				throws IOException {
			while (inputValue.hasNext())
				output.collect(key, inputValue.next());
		}
	}

	public static void main(String[] args) throws IOException {

		for (int i = 0; i < inputPaths.length; i++) {
			JobConf job = new JobConf(RecordBalance.class);
			job.addResource(new Path(confPath + "/core-site.xml"));
			job.addResource(new Path(confPath + "/mapred-site.xml"));
			job.addResource(new Path(confPath + "/hdfs-site.xml"));

			job.setJobName(RecordBalance.class.getSimpleName());
			job.setMapperClass(MapRecordOnly.class);
			job.setReducerClass(ReduceRecordOnly.class);
			job.setMapOutputKeyClass(LongWritable.class);
			job.setMapOutputValueClass(Text.class);

			job.setInputFormat(TextInputFormat.class);
			FileInputFormat.setInputPaths(job, inputPaths[i]);
			FileOutputFormat.setOutputPath(job, outputPaths[i]);
			job.setNumReduceTasks(Integer.parseInt(args[0]));

			JobClient.runJob(job);
		}
	}
}