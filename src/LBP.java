/***********************************************************************
    PEGASUS: Peta-Scale Graph Mining System
    Copyright (c) 2010
    U Kang and Christos Faloutsos
    All Rights Reserved

You may use this code without fee, for educational and research purposes.
Any for-profit use requires written consent of the copyright holders.

-------------------------------------------------------------------------
File: LinearBP.java
 - Linearized Belief Propagation
Version: 0.9
Author Email: U Kang(ukang@cs.cmu.edu), Christos Faloutsos(christos@cs.cmu.edu)
 ***********************************************************************/

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Partitioner;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

// y = y + ax
class SaxpyTextoutput extends Configured implements Tool {
	// ////////////////////////////////////////////////////////////////////
	// STAGE 1: make initial pagerank vector
	// ////////////////////////////////////////////////////////////////////

	// MapStage1:
	public static class MapStage1 extends MapReduceBase implements
			Mapper<LongWritable, Text, IntWritable, DoubleWritable> {
		private final IntWritable from_node_int = new IntWritable();
		private boolean isYpath = false;
		private boolean isXpath = false;
		private double a;

		@Override
		public void configure(JobConf job) {
			String y_path = job.get("y_path");
			String x_path = job.get("x_path");
			a = Double.parseDouble(job.get("a"));

			String input_file = job.get("map.input.file");
			if (input_file.contains(y_path))
				isYpath = true;
			else if (input_file.contains(x_path))
				isXpath = true;

			System.out.println("SaxpyTextoutput.MapStage1: map.input.file = "
					+ input_file + ", isYpath=" + isYpath + ", isXpath="
					+ isXpath + ", a=" + a);
		}

		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<IntWritable, DoubleWritable> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			int tabpos = line_text.indexOf("\t");

			int out_key = Integer.parseInt(line_text.substring(0, tabpos));
			double out_val = 0;

			if (line_text.charAt(tabpos + 1) == 'v') {
				out_val = Double.parseDouble(line_text.substring(tabpos + 2));
			} else {
				out_val = Double.parseDouble(line_text.substring(tabpos + 1));
			}

			if (isYpath) {
				output.collect(new IntWritable(out_key), new DoubleWritable(
						out_val));
			} else if (isXpath) {
				output.collect(new IntWritable(out_key), new DoubleWritable(a
						* out_val));
			}
		}
	}

	// RedStage1
	public static class RedStage1 extends MapReduceBase implements
			Reducer<IntWritable, DoubleWritable, IntWritable, Text> {
		@Override
		public void reduce(final IntWritable key,
				final Iterator<DoubleWritable> values,
				final OutputCollector<IntWritable, Text> output,
				final Reporter reporter) throws IOException {
			int i = 0;
			double val_double[] = new double[2];
			val_double[0] = 0;
			val_double[1] = 0;

			while (values.hasNext()) {
				val_double[i] = values.next().get();
				i++;
			}

			double result = val_double[0] + val_double[1];
			if (result != 0)
				output.collect(key, new Text("v" + result));
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// command line interface
	// ////////////////////////////////////////////////////////////////////
	protected int nreducers = 1;

	// Main entry point.
	public static void main(final String[] args) throws Exception {
		final int result = ToolRunner.run(new Configuration(),
				new SaxpyTextoutput(), args);

		System.exit(result);
	}

	// Print the command-line usage text.
	protected static int printUsage() {
		System.out
				.println("SaxpyTextoutput <# of reducers> <y_path> <x_path> <a>");
		// ToolRunner.printGenericCommandUsage(System.out);
		return -1;
	}

	// submit the map/reduce job.
	@Override
	public int run(final String[] args) throws Exception {
		if (args.length != 4) {
			return printUsage();
		}

		int ret_val = 0;

		nreducers = Integer.parseInt(args[0]);
		Path y_path = new Path(args[1]);
		Path x_path = new Path(args[2]);
		double param_a = Double.parseDouble(args[3]);

		System.out
				.println("\n-----===[PEGASUS: A Peta-Scale Graph Mining System]===-----\n");
		System.out.println("[PEGASUS] Computing SaxpyTextoutput. y_path="
				+ y_path.toString() + ", x_path=" + x_path.toString() + ", a="
				+ param_a + "\n");

		final FileSystem fs = FileSystem.get(getConf());

		Path saxpy_output = new Path("saxpy_output");
		if (y_path.toString().equals("saxpy_output")) {
			System.out
					.println("saxpy(): output path name is same as the input path name: changing the output path name to saxpy_output1");
			saxpy_output = new Path("saxpy_output1");
			ret_val = 1;
		}
		fs.delete(saxpy_output);

		JobClient.runJob(configSaxpyTextoutput(y_path, x_path, saxpy_output,
				param_a));

		System.out
				.println("\n[PEGASUS] SaxpyTextoutput computed. Output is saved in HDFS "
						+ saxpy_output.toString() + "\n");

		return ret_val;
		// return value : 1 (output path is saxpy_output1)
		// 0 (output path is saxpy_output)
	}

	// Configure SaxpyTextoutput
	protected JobConf configSaxpyTextoutput(Path py, Path px,
			Path saxpy_output, double a) throws Exception {
		final JobConf conf = new JobConf(getConf(), SaxpyTextoutput.class);
		conf.set("y_path", py.toString());
		conf.set("x_path", px.toString());
		conf.set("a", "" + a);
		conf.setJobName("SaxpyTextoutput");

		conf.setMapperClass(SaxpyTextoutput.MapStage1.class);
		conf.setReducerClass(SaxpyTextoutput.RedStage1.class);

		FileInputFormat.setInputPaths(conf, py, px);
		FileOutputFormat.setOutputPath(conf, saxpy_output);

		conf.setNumReduceTasks(nreducers);

		conf.setOutputKeyClass(IntWritable.class);
		conf.setMapOutputValueClass(DoubleWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}
}

class DegDist extends Configured implements Tool {
	// InOutDeg : single-count reciprocal edges. InPlusOutDeg: double-count
	// reciprocal edges
	static int InDeg = 1, OutDeg = 2, InOutDeg = 3, InPlusOutDeg = 4;

	// ////////////////////////////////////////////////////////////////////
	// PASS 1: group by node id.
	// Input : edge list
	// Output : key(node_id), value(degree)
	// ////////////////////////////////////////////////////////////////////
	public static class MapPass1 extends MapReduceBase implements
			Mapper<LongWritable, Text, IntWritable, IntWritable> {
		int deg_type = 0;

		@Override
		public void configure(JobConf job) {
			deg_type = Integer.parseInt(job.get("deg_type"));

			System.out.println("MapPass1 : configure is called. degtype = "
					+ deg_type);
		}

		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<IntWritable, IntWritable> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			if (line_text.startsWith("#") || line_text.length() == 0) // ignore
																		// comments
																		// in
																		// edge
																		// file
				return;

			String[] line = line_text.split("\t");
			IntWritable one_int = new IntWritable(1);

			if (deg_type == OutDeg) {
				IntWritable key_node_int = new IntWritable();
				key_node_int.set(Integer.parseInt(line[0]));

				output.collect(key_node_int, one_int);
			} else if (deg_type == InDeg) {
				output.collect(new IntWritable(Integer.parseInt(line[1])),
						one_int);
			} else if (deg_type == InOutDeg) { // emit both
				IntWritable from_node_int = new IntWritable(
						Integer.parseInt(line[0]));
				IntWritable to_node_int = new IntWritable(
						Integer.parseInt(line[1]));

				output.collect(from_node_int, to_node_int);
				output.collect(to_node_int, from_node_int);
			} else if (deg_type == InPlusOutDeg) { // emit both
				IntWritable from_node_int = new IntWritable(
						Integer.parseInt(line[0]));
				IntWritable to_node_int = new IntWritable(
						Integer.parseInt(line[1]));

				output.collect(from_node_int, one_int);
				output.collect(to_node_int, one_int);
			}
		}
	}

	public static class RedPass1 extends MapReduceBase implements
			Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
		int deg_type = 0;

		@Override
		public void configure(JobConf job) {
			deg_type = Integer.parseInt(job.get("deg_type"));

			System.out.println("RedPass1 : configure is called. degtype = "
					+ deg_type);
		}

		@Override
		public void reduce(final IntWritable key,
				final Iterator<IntWritable> values,
				OutputCollector<IntWritable, IntWritable> output,
				final Reporter reporter) throws IOException {
			int degree = 0;

			if (deg_type != InOutDeg) {
				while (values.hasNext()) {
					int cur_degree = values.next().get();
					degree += cur_degree;
				}

				output.collect(key, new IntWritable(degree));
			} else if (deg_type == InOutDeg) {
				Set<Integer> outEdgeSet = new TreeSet<Integer>();
				while (values.hasNext()) {
					int cur_outedge = values.next().get();
					outEdgeSet.add(cur_outedge);
				}

				output.collect(key, new IntWritable(outEdgeSet.size()));
			}
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////
	// PASS 2: group by degree
	// Input : key(node id), value(degree)
	// Output : key(degree), value(count)
	// //////////////////////////////////////////////////////////////////////////////////////////////
	public static class MapPass2 extends MapReduceBase implements
			Mapper<LongWritable, Text, IntWritable, IntWritable> {
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<IntWritable, IntWritable> output,
				final Reporter reporter) throws IOException {
			String[] line = value.toString().split("\t");

			output.collect(new IntWritable(Integer.parseInt(line[1])),
					new IntWritable(1));
		}
	}

	public static class RedPass2 extends MapReduceBase implements
			Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
		@Override
		public void reduce(final IntWritable key,
				final Iterator<IntWritable> values,
				final OutputCollector<IntWritable, IntWritable> output,
				final Reporter reporter) throws IOException {
			int count = 0;

			while (values.hasNext()) {
				int cur_count = values.next().get();
				count += cur_count;
			}

			output.collect(key, new IntWritable(count));
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// command line interface
	// ////////////////////////////////////////////////////////////////////
	protected Path edge_path = null;
	protected Path node_deg_path = null;
	protected Path deg_count_path = null;
	protected int nreducer = 1;
	protected int deg_type;

	// Main entry point.
	public static void main(final String[] args) throws Exception {
		final int result = ToolRunner.run(new Configuration(), new DegDist(),
				args);

		System.exit(result);
	}

	// Print the command-line usage text.
	protected static int printUsage() {
		System.out
				.println("DegDist <edge_path> <node_deg_path> <deg_count_path> <in or out or inout or inpout> <# of reducer>");

		ToolRunner.printGenericCommandUsage(System.out);

		return -1;
	}

	// submit the map/reduce job.
	@Override
	public int run(final String[] args) throws Exception {
		if (args.length != 5) {
			return printUsage();
		}

		edge_path = new Path(args[0]);
		node_deg_path = new Path(args[1]);
		deg_count_path = new Path(args[2]);

		String deg_type_str = "In";
		deg_type = InDeg;
		if (args[3].compareTo("out") == 0) {
			deg_type = OutDeg;
			deg_type_str = "Out";
		} else if (args[3].compareTo("inout") == 0) {
			deg_type = InOutDeg;
			deg_type_str = "InOut";
		} else if (args[3].compareTo("inpout") == 0) {
			deg_type = InPlusOutDeg;
			deg_type_str = "InPlusOut";
		}

		nreducer = Integer.parseInt(args[4]);

		System.out
				.println("\n-----===[PEGASUS: A Peta-Scale Graph Mining System]===-----\n");
		System.out
				.println("[PEGASUS] Computing degree distribution. Degree type = "
						+ deg_type_str + "\n");

		// run job
		JobClient.runJob(configPass1());
		JobClient.runJob(configPass2());

		System.out.println("\n[PEGASUS] Degree distribution computed.");
		System.out.println("[PEGASUS] (NodeId, Degree) is saved in HDFS "
				+ args[1] + ", (Degree, Count) is saved in HDFS " + args[2]
				+ "\n");

		return 0;
	}

	// Configure pass1
	protected JobConf configPass1() throws Exception {
		final JobConf conf = new JobConf(getConf(), DegDist.class);
		conf.set("deg_type", "" + deg_type);

		conf.setJobName("DegDist_pass1");

		conf.setMapperClass(MapPass1.class);
		conf.setReducerClass(RedPass1.class);
		if (deg_type != InOutDeg) {
			conf.setCombinerClass(RedPass1.class);
		}

		FileSystem fs = FileSystem.get(getConf());
		fs.delete(node_deg_path);

		FileInputFormat.setInputPaths(conf, edge_path);
		FileOutputFormat.setOutputPath(conf, node_deg_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(IntWritable.class);
		conf.setOutputValueClass(IntWritable.class);

		return conf;
	}

	// Configure pass2
	protected JobConf configPass2() throws Exception {
		final JobConf conf = new JobConf(getConf(), DegDist.class);

		conf.setJobName("DegDist_pass2");

		conf.setMapperClass(MapPass2.class);
		conf.setReducerClass(RedPass2.class);
		conf.setCombinerClass(RedPass2.class);

		FileSystem fs = FileSystem.get(getConf());
		fs.delete(deg_count_path);

		FileInputFormat.setInputPaths(conf, node_deg_path);
		FileOutputFormat.setOutputPath(conf, deg_count_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(IntWritable.class);
		conf.setOutputValueClass(IntWritable.class);

		return conf;
	}

}

class MatvecNaiveSecondarySort extends Configured implements Tool {

	// Comparator class that uses only the first token for the comparison.
	// MyValueGroupingComparator and MyPartition are used for secondary-sort for
	// building N.
	// reference: src/examples/org/apache/hadoop/examples/SecondarySort.java
	public static class MyValueGroupingComparator implements
			RawComparator<Text> {
		@Override
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return WritableComparator.compareBytes(b1, s1, Integer.SIZE / 8,
					b2, s2, Integer.SIZE / 8);
		}

		@Override
		public int compare(Text t1, Text t2) {
			String str1 = t1.toString();
			String str2 = t2.toString();
			int pos1 = str1.indexOf("\t");
			int pos2 = str2.indexOf("\t");
			long l = (Long.parseLong(str1.substring(0, pos1)));
			long r = (Long.parseLong(str2.substring(0, pos2)));

			return l == r ? 0 : (l < r ? -1 : 1);
		}
	}

	public static class MyKeyComparator implements RawComparator<Text> {
		@Override
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return WritableComparator.compareBytes(b1, s1, Integer.SIZE / 8,
					b2, s2, Integer.SIZE / 8);
		}

		@Override
		public int compare(Text t1, Text t2) {
			String str1 = t1.toString();
			String str2 = t2.toString();
			int pos1 = str1.indexOf("\t");
			int pos2 = str2.indexOf("\t");
			long l = (Long.parseLong(str1.substring(0, pos1)));
			long r = (Long.parseLong(str2.substring(0, pos2)));

			if (l != r)
				return (l < r ? -1 : 1);

			l = Long.parseLong(str1.substring(pos1 + 1));
			r = Long.parseLong(str2.substring(pos2 + 1));

			return l == r ? 0 : (l < r ? -1 : 1);
		}
	}

	// Partitioner class that uses only the first token for the partition.
	// MyValueGroupingComparator and MyPartition are used for secondary-sort for
	// building N.
	public static class MyPartition<V2> implements Partitioner<Text, V2> {
		@Override
		public void configure(JobConf job) {
		}

		@Override
		public int getPartition(Text key, V2 value, int numReduceTasks) {
			// System.out.println("[DEBUG] getPartition. key=" + key );
			String[] tokens = key.toString().split("\t");
			int partition = (int) (Long.parseLong(tokens[0])) % numReduceTasks;

			return partition;
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// PASS 1: Hash join using Vector.rowid == Matrix.colid
	// ////////////////////////////////////////////////////////////////////
	public static class MapPass1 extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, Text> {
		int makesym = 0;
		int transpose = 0;
		int ignore_weights = 0;

		@Override
		public void configure(JobConf job) {
			makesym = Integer.parseInt(job.get("makesym"));
			transpose = Integer.parseInt(job.get("transpose"));
			ignore_weights = Integer.parseInt(job.get("ignore_weights"));

			String input_file = job.get("map.input.file");

			System.out.println("MatvecNaiveSecondarySort.MapPass1: makesym = "
					+ makesym);
			System.out.println("input_file = " + input_file);
		}

		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<Text, Text> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			if (line_text.startsWith("#")) // ignore comments in edge file
				return;

			final String[] line = line_text.split("\t");

			// System.out.println("[DEBUG] line_text=[" + line_text + "]");

			if (line.length == 2 || ignore_weights == 1) { // vector : ROWID
															// VALUE('vNNNN')
				if (line[1].charAt(0) == 'v') { // vector : ROWID VALUE('vNNNN')
					String key_str = line[0] + "\t0";
					// System.out.println("[DEBUG] MapPass1 key=[" + key_str +
					// "], value=[" + line[1] + "]");
					output.collect(new Text(key_str), new Text(line[1]));
				} else { // edge : ROWID COLID
					if (transpose == 0) {
						// System.out.println("[DEBUG] MapPass1 key=[" + line[1]
						// + "\t1], value=[" + line[0] + "]");
						output.collect(new Text(line[1] + "\t1"), new Text(
								line[0]));
						if (makesym == 1)
							output.collect(new Text(line[0] + "\t1"), new Text(
									line[1]));
					} else {
						output.collect(new Text(line[0] + "\t1"), new Text(
								line[1]));
						if (makesym == 1)
							output.collect(new Text(line[1] + "\t1"), new Text(
									line[0]));
					}
				}
			} else if (line.length == 3) { // edge: ROWID COLID VALUE
				if (transpose == 0) {
					String key_str = line[1] + "\t1";
					String value_str = line[0] + "\t" + line[2];
					// System.out.println("[DEBUG] MapPass1 key=[" + key_str +
					// "], value=[" + value_str + "]");

					output.collect(new Text(key_str), new Text(value_str));
					if (makesym == 1)
						output.collect(new Text(line[0] + "\t1"), new Text(
								line[1] + "\t" + line[2]));
				} else {
					output.collect(new Text(line[0] + "\t1"), new Text(line[1]
							+ "\t" + line[2]));
					if (makesym == 1)
						output.collect(new Text(line[1] + "\t1"), new Text(
								line[0] + "\t" + line[2]));
				}
			}
		}
	}

	public static class RedPass1 extends MapReduceBase implements
			Reducer<Text, Text, LongWritable, DoubleWritable> {
		@Override
		public void reduce(final Text key, final Iterator<Text> values,
				final OutputCollector<LongWritable, DoubleWritable> output,
				final Reporter reporter) throws IOException {
			// int i;
			double vector_val = 0;
			boolean isValReceived = false;

			while (values.hasNext()) {
				String line_text = values.next().toString();

				// System.out.println("[DEBUG] RedPass1. Key=[" + key +
				// "], val=[" + line_text + "]");

				if (isValReceived == false) {
					if (line_text.charAt(0) == 'v') {
						vector_val = Double.parseDouble(line_text.substring(1));
						// System.out.println("[DEBUG] RedPass1. HAPPY EVENT Key=["
						// + key + "], val=[" + line_text + "]");
					} else {
						return;
						// System.out.println("[DEBUG] RedPass1. FATAL ERROR Key=["
						// + key + "], val=[" + line_text + "]");
						// vector_val = Double.parseDouble( line_text );
					}
					isValReceived = true;
				} else {
					String[] tokens = line_text.split("\t");

					if (tokens.length == 1) { // edge : ROWID
						// System.out.println("[DEBUG] RedPass1. outputting key=["
						// + tokens[0] + "], val=[" + vector_val + "]");

						output.collect(
								new LongWritable(Long.parseLong(tokens[0])),
								new DoubleWritable(vector_val));
					} else { // edge : ROWID VALUE
						output.collect(
								new LongWritable(Long.parseLong(tokens[0])),
								new DoubleWritable(vector_val
										* Double.parseDouble(tokens[1])));
					}
				}
			}
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// PASS 2: merge partial multiplication results
	// ////////////////////////////////////////////////////////////////////
	public static class MapPass2 extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {
		private final LongWritable from_node_int = new LongWritable();

		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			if (line_text.startsWith("#")) // ignore comments in edge file
				return;

			final String[] line = line_text.split("\t");
			from_node_int.set(Long.parseLong(line[0]));
			output.collect(from_node_int,
					new Text("v" + Double.parseDouble(line[1])));
		}
	}

	public static class RedPass2 extends MapReduceBase implements
			Reducer<LongWritable, Text, LongWritable, Text> {
		@Override
		public void reduce(final LongWritable key, final Iterator<Text> values,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			double next_rank = 0;

			while (values.hasNext()) {
				String cur_value_str = values.next().toString();
				next_rank += Double.parseDouble(cur_value_str.substring(1));
			}

			output.collect(key, new Text("v" + next_rank));
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// command line interface
	// ////////////////////////////////////////////////////////////////////
	protected Path edge_path = null;
	protected Path tempmv_path = null;
	protected Path output_path = null;
	protected Path vector_path = null;
	protected int number_nodes = 0;
	protected int nreducer = 1;
	int makesym = 0;
	int transpose = 0;
	int ignore_weights = 0;

	// Main entry point.
	public static void main(final String[] args) throws Exception {
		final int result = ToolRunner.run(new Configuration(),
				new MatvecNaiveSecondarySort(), args);

		System.exit(result);
	}

	// matrix-vecotr multiplication with secondary sort
	public static void MatvecNaiveSS(Configuration conf, int nreducer,
			String mat_path, String vec_path, String out_path, int transpose,
			int makesym) throws Exception {
		System.out.println("Running MatvecNaiveSS: mat_path=" + mat_path
				+ ", vec_path=" + vec_path);
		int ignore_weights = 0;

		String[] args = new String[8];
		args[0] = new String("" + mat_path);
		args[1] = new String("temp_matvecnaive_ss" + vec_path);
		args[2] = new String(out_path);
		args[3] = new String("" + nreducer);
		if (makesym == 1)
			args[4] = "makesym";
		else
			args[4] = "nosym";
		args[5] = new String(vec_path);
		args[6] = new String("" + transpose);
		args[7] = new String("" + ignore_weights);

		ToolRunner.run(conf, new MatvecNaiveSecondarySort(), args);
		System.out.println("Done MatvecNaiveSS. Output is saved in HDFS "
				+ out_path);

		return;
	}

	// Print the command-line usage text.
	protected static int printUsage() {
		System.out
				.println("MatvecNaiveSecondarySort <edge_path> <tempmv_path> <output_path> <# of reducers> <makesym or nosym> <vector path>");

		ToolRunner.printGenericCommandUsage(System.out);

		return -1;
	}

	// submit the map/reduce job.
	@Override
	public int run(final String[] args) throws Exception {
		if (args.length < 5) {
			return printUsage();
		}

		edge_path = new Path(args[0]);
		tempmv_path = new Path(args[1]);
		output_path = new Path(args[2]);
		nreducer = Integer.parseInt(args[3]);
		if (args[4].equals("makesym"))
			makesym = 1;
		if (args.length > 5)
			vector_path = new Path(args[5]);
		if (args.length > 6)
			transpose = Integer.parseInt(args[6]);
		if (args.length > 7)
			ignore_weights = Integer.parseInt(args[7]);

		final FileSystem fs = FileSystem.get(getConf());
		fs.delete(tempmv_path);
		fs.delete(output_path);

		System.out.println("Starting MatvecNaiveSecondarySort. tempmv_path="
				+ args[1] + ", transpose=" + transpose);

		JobClient.runJob(configPass1());
		JobClient.runJob(configPass2());

		fs.delete(tempmv_path);
		System.out.println("Done. output is saved in HDFS " + args[2]);

		return 0;
	}

	// Configure pass1
	protected JobConf configPass1() throws Exception {
		final JobConf conf = new JobConf(getConf(),
				MatvecNaiveSecondarySort.class);
		conf.set("number_nodes", "" + number_nodes);
		conf.set("makesym", "" + makesym);
		conf.set("transpose", "" + transpose);
		conf.set("ignore_weights", "" + ignore_weights);

		conf.setJobName("MatvecNaiveSecondarySort_pass1");
		System.out.println("Configuring MatvecNaiveSecondarySort. makesym="
				+ makesym);

		conf.setMapperClass(MapPass1.class);
		conf.setReducerClass(RedPass1.class);
		conf.setPartitionerClass(MyPartition.class);
		conf.setOutputValueGroupingComparator(MyValueGroupingComparator.class);
		// conf.setOutputKeyComparatorClass(MyKeyComparator.class);

		if (vector_path == null)
			FileInputFormat.setInputPaths(conf, edge_path);
		else
			FileInputFormat.setInputPaths(conf, edge_path, vector_path);
		FileOutputFormat.setOutputPath(conf, tempmv_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(DoubleWritable.class);
		conf.setMapOutputKeyClass(Text.class);
		conf.setMapOutputValueClass(Text.class);

		return conf;
	}

	// Configure pass2
	protected JobConf configPass2() throws Exception {
		final JobConf conf = new JobConf(getConf(),
				MatvecNaiveSecondarySort.class);
		conf.set("number_nodes", "" + number_nodes);

		conf.setJobName("MatvecNaive_pass2");

		conf.setMapperClass(MapPass2.class);
		conf.setReducerClass(RedPass2.class);
		conf.setCombinerClass(RedPass2.class);

		FileInputFormat.setInputPaths(conf, tempmv_path);
		FileOutputFormat.setOutputPath(conf, output_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(LongWritable.class);
		// conf.setMapOutputValueClass(DoubleWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

}

public class LBP extends Configured implements Tool {

	public static class MapComputeM extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {
		double c = 1;
		double a = 1;
		boolean isDeg = false;

		@Override
		public void configure(JobConf job) {
			c = Double.parseDouble(job.get("c"));
			a = Double.parseDouble(job.get("a"));

			String input_file = job.get("map.input.file");
			if (input_file.indexOf("dd_node_deg") >= 0)
				isDeg = true;

			System.out.println("[MapComputeM] c = " + c + ", a = " + a
					+ ", input_file = " + input_file + ", isDeg = " + isDeg);
		}

		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			if (line_text.startsWith("#")) // ignore comments in edge file
				return;

			final String[] tokens = line_text.split("\t");
			if (tokens.length < 2)
				return;

			if (isDeg == true) {
				double deg = -1 * a * Double.parseDouble(tokens[1]);
				output.collect(new LongWritable(Long.parseLong(tokens[0])),
						new Text("" + tokens[0] + "\t" + deg));
			} else {
				if (tokens.length == 2) {
					output.collect(new LongWritable(Long.parseLong(tokens[0])),
							new Text("" + tokens[1] + "\t" + c));
				}
			}
		}
	}

	public static class MapInitPrior extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, Text> {
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<Text, Text> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			if (line_text.startsWith("#")) // ignore comments in edge file
				return;

			final String[] tokens = line_text.split("\t");
			if (tokens.length < 2)
				return;

			double prior_val = Double.parseDouble(tokens[1].substring(1)) - 0.5;

			output.collect(new Text(tokens[0]), new Text("v" + prior_val));
		}
	}

	protected static void copyToLocalFile(Configuration conf, Path hdfs_path,
			Path local_path) throws Exception {
		FileSystem fs = FileSystem.get(conf);

		// read the result
		fs.copyToLocalFile(hdfs_path, local_path);
	}

	// ////////////////////////////////////////////////////////////////////
	// command line interface
	// ////////////////////////////////////////////////////////////////////
	String edge_path_str = null;
	String prior_path_str = null;
	String output_path_str = null;

	protected Path edge_path = null;
	protected Path prior_path = null;
	protected Path message_cur_path = new Path("bp_message_cur");
	protected Path message_next_path = new Path("bp_message_next");
	protected Path error_check_path = new Path("bp_error_check");
	protected Path error_check_sum_path = new Path("bp_error_check_sum");
	protected Path output_path = null;
	protected long number_edges = 0;
	protected int max_iter = 32;
	protected int nreducers = 1;
	protected int nstate = 2;
	String edge_potential_str = "";
	FileSystem fs;
	double hh = 0.0001;
	protected String local_output_path = "lbp_local";
	int number_nodes = 0;
	double a = 0;
	double c = 0;

	// Main entry point.
	public static void main(final String[] args) throws Exception {
		final int result = ToolRunner.run(new Configuration(), new LBP(), args);

		System.exit(result);
	}

	// Print the command-line usage text.
	protected static int printUsage() {
		System.out
				.println("LinearBP <edge_path> <prior_path> <output_path> <# of nodes> <# of reducer> <max iteration> <L1 or L2>");

		ToolRunner.printGenericCommandUsage(System.out);

		return -1;
	}

	public static Path SaxpyTextoutput(Configuration conf, int nreducer,
			String py_name, String px_name, String outpath_name, double a)
			throws Exception {
		// System.out.println("Running Saxpy: py=" + py.getName() + ", px=" +
		// px.getName() + ", a=" +a);

		String[] args = new String[4];
		args[0] = new String("" + nreducer);
		args[1] = new String(py_name);
		args[2] = new String(px_name);
		args[3] = new String("" + a);
		int saxpy_result = ToolRunner.run(conf, new SaxpyTextoutput(), args);

		// Path ret_path = null;
		Path out_path = new Path(outpath_name);

		FileSystem fs = FileSystem.get(conf);
		fs.delete(out_path, true);

		if (saxpy_result == 1)
			fs.rename(new Path("saxpy_output1"), out_path);
		else
			fs.rename(new Path("saxpy_output"), out_path);

		return out_path;
	}

	// submit the map/reduce job.
	@Override
	public int run(String[] args) throws Exception {
		if (args.length != 7) {
			for (String a : args) {
				System.out.println(a);
			}
			System.out.println("args num: " + args.length);
			return printUsage();
		}

		edge_path_str = args[0];
		prior_path_str = args[1];
		output_path_str = args[2];

		edge_path = new Path(edge_path_str);
		prior_path = new Path(prior_path_str);
		output_path = new Path(output_path_str);
		Path vector_path = new Path("lbp_cur_vector");

		number_nodes = Integer.parseInt(args[3]);
		nreducers = Integer.parseInt(args[4]);
		nreducers = 1;
		max_iter = Integer.parseInt(args[5]);

		String hh_method = args[6];

		System.out.println("edge_path=" + args[0] + ", prior_path=" + args[1]
				+ ", output_path=" + args[2] + ", nreducers=" + nreducers
				+ ", maxiter=" + max_iter + ", hh_method=" + hh_method);

		fs = FileSystem.get(getConf());

		int cur_iter = 1;
		// Step 1. compute h_h
		System.out
				.println("####################################################");
		System.out.println("Step 1. Computing h_h");

		// (1.1) compute degree
		args = new String[5];
		args[0] = edge_path_str;
		args[1] = "dd_node_deg";
		args[2] = "dd_deg_count";
		args[3] = "in";
		args[4] = new String("" + nreducers);
		ToolRunner.run(getConf(), new DegDist(), args);

		if (hh_method.startsWith("PRE")) {
			hh = Double.parseDouble(hh_method.substring(3));
		}

		double temp_denom = (1 - 4 * hh * hh);
		a = 4 * hh * hh / temp_denom;
		c = 2 * hh / temp_denom;

		System.out.println("h_h = " + hh + ", a = " + a + ", c = " + c);

		// Step 2. Init prior
		// Input: prior_path
		// Output: vector_path
		System.out
				.println("####################################################");
		System.out.println("Step 2. computing initial vector");
		JobClient.runJob(configInitPrior(prior_path, vector_path));

		// Step 2. compute M = cA - aD
		System.out
				.println("####################################################");
		System.out.println("Step 3. Computing M = cA - aD");
		Path m_path = new Path("lbp_m_path");
		JobClient.runJob(configComputeM(edge_path, new Path("dd_node_deg"),
				m_path, c, a));

		for (int i = cur_iter; i <= max_iter; i++) {
			System.out.println("   *** ITERATION " + (i) + "/" + max_iter
					+ " ***");

			MatvecNaiveSecondarySort.MatvecNaiveSS(getConf(), nreducers,
					"lbp_m_path", "lbp_cur_vector", "mv_output", 0, 0);

			Path temp_output_path = new Path("lbp_temp_output");

			if (i == 1)
				SaxpyTextoutput(getConf(), nreducers, "lbp_cur_vector",
						"mv_output", "lbp_temp_output", 1.0);
			else
				SaxpyTextoutput(getConf(), nreducers, output_path_str,
						"mv_output", "lbp_temp_output", 1.0);

			// rotate directory
			fs.delete(output_path);
			fs.rename(temp_output_path, output_path);

			fs.delete(vector_path);
			fs.rename(new Path("mv_output"), vector_path);

		}

		System.out
				.println("Linear BP finished. The belief vector is in the HDFS "
						+ output_path_str);

		return 0;
	}

	protected JobConf configInitPrior(Path prior_path, Path init_vector_path)
			throws Exception {
		final JobConf conf = new JobConf(getConf(), LBP.class);
		conf.setJobName("LBP_InitPrior");

		conf.setMapperClass(MapInitPrior.class);

		FileSystem fs = FileSystem.get(getConf());
		fs.delete(init_vector_path);

		FileInputFormat.setInputPaths(conf, prior_path);
		FileOutputFormat.setOutputPath(conf, init_vector_path);

		conf.setNumReduceTasks(0);

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

	protected JobConf configComputeM(Path edge_path, Path d_path, Path m_path,
			double c, double a) throws Exception {
		final JobConf conf = new JobConf(getConf(), LBP.class);
		conf.set("c", "" + c);
		conf.set("a", "" + a);
		conf.setJobName("LBP_ComputeMin");

		conf.setMapperClass(MapComputeM.class);

		FileSystem fs = FileSystem.get(getConf());
		fs.delete(m_path);

		FileInputFormat.setInputPaths(conf, edge_path, d_path);
		FileOutputFormat.setOutputPath(conf, m_path);

		conf.setNumReduceTasks(0);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

}
