/***********************************************************************
    PEGASUS: Peta-Scale Graph Mining System
    Authors: U Kang, Duen Horng Chau, and Christos Faloutsos

This software is licensed under Apache License, Version 2.0 (the  "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-------------------------------------------------------------------------
File: Belief Propagation.java
 - A main class for belief propagation.
Version: 2.0
 ***********************************************************************/

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
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
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class BP extends Configured implements Tool {
	// Identity Mapper
	public static class MapIdentityDouble extends MapReduceBase implements
			Mapper<LongWritable, Text, IntWritable, DoubleWritable> {
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<IntWritable, DoubleWritable> output,
				final Reporter reporter) throws IOException {
			String line_text = value.toString();
			int tabpos = line_text.indexOf("\t");

			int out_key = Integer.parseInt(line_text.substring(0, tabpos));

			output.collect(
					new IntWritable(out_key),
					new DoubleWritable(Double.parseDouble(line_text
							.substring(tabpos + 1))));
		}
	}

	// Sum Reducer (type: double)
	public static class RedSumDouble extends MapReduceBase implements
			Reducer<IntWritable, DoubleWritable, IntWritable, DoubleWritable> {
		@Override
		public void reduce(final IntWritable key,
				final Iterator<DoubleWritable> values,
				final OutputCollector<IntWritable, DoubleWritable> output,
				final Reporter reporter) throws IOException {
			double sum = 0;

			while (values.hasNext()) {
				double cur_val = values.next().get();
				sum += cur_val;
			}

			output.collect(key, new DoubleWritable(sum));
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// PASS 1: Initialize Belief
	// Input: bi-directional edge_file (src, dst).
	// Note that the edge should be symmetric (i.e., (s, d) and (d, s) should
	// both exist).
	// - Output: initial message = {(src, dst, "s" + state1_prob, ..., "s" +
	// state(K-1)_prob)}
	// ////////////////////////////////////////////////////////////////////
	public static class MapInitializeBelief extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, Text> {
		int nstate = 2; // number of state
		double init_message_prob = 0.5; // the probablity for each state, for
										// the initial message.

		@Override
		public void configure(JobConf job) {
			nstate = Integer.parseInt(job.get("nstate"));
			init_message_prob = 1.0 / nstate;

			System.out.println("[MapInitializeBelief] nstate: nstate = "
					+ nstate + ", init_message_prob=" + init_message_prob);
		}

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

			String message_str = "";
			for (int i = 0; i < nstate - 1; i++) {
				message_str += ("s" + init_message_prob);
				if (i < nstate - 2)
					message_str += "\t";
			}

			output.collect(new Text(tokens[0] + "\t" + tokens[1]), new Text(
					message_str));
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////
	// STAGE 2.1: Update Messages
	// - Input: current message (bp_message_cur) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)},
	// prior matrix(bp_prior) = {(nodeid, "p" + state1_prior, ..., "p" +
	// state(K-1)_prior)}
	// - Output: updated message (bp_message_next) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)}
	// //////////////////////////////////////////////////////////////////////////////////////////////
	public static class MapUpdateMessage extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {
		// Identity mapper
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			final String[] line = value.toString().split("\t");

			String value_str = "";
			for (int i = 1; i < line.length; i++) {
				value_str += line[i];
				if (i < line.length - 1)
					value_str += "\t";
			}

			output.collect(new LongWritable(Long.parseLong(line[0])), new Text(
					value_str));
		}
	}

	public static class RedUpdateMessage extends MapReduceBase implements
			Reducer<LongWritable, Text, LongWritable, Text> {
		double[][] ep;
		int nstate = 2;
		double threshold_underflow = 1e-50;
		double threshold_multiplier = 1e50;

		@Override
		public void configure(JobConf job) {
			nstate = Integer.parseInt(job.get("nstate"));
			String compat_matrix_str = job.get("compat_matrix_str");
			String[] tokens = compat_matrix_str.split("\t");

			ep = new double[nstate][nstate];
			int cur_seq = 0;
			int row, col;
			for (row = 0; row < nstate; row++) {
				double cumul_sum = 0;
				for (col = 0; col < nstate - 1; col++) {
					ep[row][col] = Double.parseDouble(tokens[cur_seq++]);
					cumul_sum += ep[row][col];
				}
				ep[row][col] = 1 - cumul_sum;
			}

			// System.out.println("[RedUpdateMessage] nstate = " + nstate
			// + ". Compatibility Matrix=");
			// for (row = 0; row < nstate; row++) {
			// for (col = 0; col < nstate; col++) {
			// System.out.print("" + ep[row][col] + "\t");
			// }
			// System.out.println("");
			// }
		}

		@Override
		public void reduce(final LongWritable key, final Iterator<Text> values,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			double[] prior = new double[nstate];
			double[] temp_s = new double[nstate];

			int i;

			double default_prior = 1.0 / nstate;
			for (i = 0; i < nstate; i++) {
				prior[i] = default_prior;
				temp_s[i] = 1;
			}

			Map<Long, double[]> msg_map = new HashMap<Long, double[]>(); // did,
																			// m_ds(s_1),
																			// ...,
																			// m_ds(s_(K-1))

			// System.out.println("[DEBUG RedStage2] key=" + key.toString() );

			while (values.hasNext()) {
				String cur_value_str = values.next().toString();
				// System.out.println("[DEBUG RedStage2] val=" + cur_value_str
				// );
				String[] tokens = cur_value_str.split("\t");

				if (cur_value_str.startsWith("p")) { // prior vector
					double sum = 0;
					for (i = 0; i < nstate - 1; i++) {
						prior[i] = Double.parseDouble(tokens[i].substring(1));
						sum += prior[i];
					}
					prior[i] = 1.0 - sum;

					// System.out.println("prior[0]=" + prior[0] + ", prior[1]="
					// + prior[1]);
				} else { // message vector. starts with did.
					double sum = 0;
					double[] cur_states = new double[nstate - 1];
					for (i = 0; i < nstate - 1; i++) {
						double cur_state_prob = Double
								.parseDouble(tokens[i + 1].substring(1));

						temp_s[i] *= cur_state_prob;
						sum += cur_state_prob;

						cur_states[i] = cur_state_prob;
					}
					temp_s[i] *= (1.0 - sum);

					// System.out.println("dst:" + tokens[0] + ", cur_s1 = " +
					// cur_s1 + ", cur_s2 = " + (1-cur_s1) );

					// avoid underflow.
					for (i = 0; i < nstate; i++) {
						if (temp_s[i] < threshold_underflow) {
							// System.out.println("[DEBUG] multiplying scalars!!!");
							int j;
							double max_val = 0;
							for (j = 0; j < nstate; j++)
								if (temp_s[j] > max_val)
									max_val = temp_s[j];

							double mult_val = threshold_multiplier / max_val;

							for (j = 0; j < nstate; j++)
								temp_s[j] *= mult_val;
							break;
						}
					}

					msg_map.put(Long.parseLong(tokens[0]), cur_states);
				}
			}

			// For all the (dst, msg(s1), ..., msg(s(K-1))) in the map, output
			// updated messages.
			Iterator it = msg_map.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pairs = (Map.Entry) it.next();

				long cur_dst = ((Long) pairs.getKey()).longValue();
				double[] cur_s = new double[nstate];

				double[] temp_km1_s = (double[]) pairs.getValue();// ((Double)pairs.getValue()).doubleValue();

				cur_s[nstate - 1] = 1.0;
				for (i = 0; i < nstate - 1; i++) {
					cur_s[i] = temp_km1_s[i];
					cur_s[nstate - 1] -= cur_s[i];
				}

				// System.out.println("cur_s[0] = " + cur_s[0] + ", cur_s[1]=" +
				// cur_s[1]);

				double[] new_msg = new double[nstate];
				for (int s = 0; s < nstate; s++) {
					new_msg[s] = 0;
					for (int t = 0; t < nstate; t++) {
						// System.out.println("[DEBUG] s=" + s + ", t=" + t
						// +", prior[t]=" + prior[t] + ", ep[t][s]=" + ep[t][s]
						// + ", temp_s[t]=" + temp_s[t] + ", cur_s[t] = " +
						// cur_s[t]);
						new_msg[s] += prior[t] * ep[t][s] * temp_s[t]
								/ cur_s[t];
					}
				}

				// System.out.println("[DEBUG] UNNORMALIZED src=" + key.get() +
				// ", cur_dst=" + cur_dst + ", new_msg[0]=" + new_msg[0] +
				// ", new_msg[1]=" + new_msg[1]);
				String debug_saved_str = "";
				for (i = 0; i < nstate; i++)
					debug_saved_str += "s" + new_msg[i];

				// normalize msg
				double sum = 0;
				for (i = 0; i < nstate; i++)
					sum += new_msg[i];

				for (i = 0; i < nstate; i++)
					new_msg[i] /= sum;

				// System.out.println("[DEBUG] NORMALIZED src=" + key.get() +
				// ", cur_dst=" + cur_dst + ", new_msg[0]=" + new_msg[0] +
				// ", new_msg[1]=" + (1-new_msg[0]));

				String out_val = "" + key.get();

				for (i = 0; i < nstate - 1; i++) {
					out_val += "\ts" + new_msg[i];

					if (Double.isNaN(new_msg[i])) {
						out_val += "DEBUG" + debug_saved_str + "DEBUG";
					}

				}

				output.collect(new LongWritable(cur_dst), new Text(out_val));
				// System.out.println(pairs.getKey() + " = " +
				// pairs.getValue());
			}

			msg_map.clear();
		}
	}
	// //////////////////////////////////////////////////////////////////////////////////////////////
	// STAGE 2.3: Check Error
	// - Input: current message (bp_message_cur) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)},
	// prior matrix(bp_prior) = {(nodeid, "p" + state1_prior, ..., "p" +
	// state(K-1)_prior)}
	// - Output: updated message (bp_message_next) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)}
	// //////////////////////////////////////////////////////////////////////////////////////////////
	public static class MapCheckErr extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {

		String prefix = "";

		@Override
		public void configure(JobConf job) {
			String input_file = job.get("map.input.file");
			if(input_file.contains("cur")){
				prefix = "o";
			}else{
				prefix = "n";
			}
//				System.out.println("Processing " + input_file);
		}

		// Identity mapper
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			final String[] line = value.toString().split("\t");

			String value_str = "";
			for (int i = 1; i < line.length; i++) {
				value_str += line[i];
				if (i < line.length - 1)
					value_str += "\t";
			}

			output.collect(new LongWritable(Long.parseLong(line[0])), new Text(
					prefix + value_str));
		}
	}

	public static class RedCheckErr extends MapReduceBase implements
			Reducer<LongWritable, Text, LongWritable, Text> {

		int nstate = 2;

		@Override
		public void configure(JobConf job) {
			nstate = Integer.parseInt(job.get("nstate"));
		}
		@Override
		public void reduce(final LongWritable key, final Iterator<Text> values,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {

			Map<Long, double[]> n_msg_map = new HashMap<Long, double[]>(); // did,
																			// m_ds(s_1),
																			// ...,
																			// m_ds(s_(K-1))
			Map<Long, double[]> o_msg_map = new HashMap<Long, double[]>();

			// System.out.println("[DEBUG RedStage2] key=" + key.toString() );

			while (values.hasNext()) {
				String cur_value_str = values.next().toString();
				// System.out.println("[DEBUG RedStage2] val=" + cur_value_str
				// );
				String[] tokens = cur_value_str.substring(1).split("\t");
				double[] cur_states = new double[nstate];
				double sum = 0;
				for (int i = 0; i < nstate - 1; i++) {
					double cur_state_prob = Double
							.parseDouble(tokens[i + 1].substring(1));
					cur_states[i] = cur_state_prob;
					sum += cur_state_prob;
				}
				cur_states[nstate - 1] = 1.0 - sum;

				if(cur_value_str.startsWith("o")){
					o_msg_map.put(Long.parseLong(tokens[0]), cur_states);
				}else{
					n_msg_map.put(Long.parseLong(tokens[0]), cur_states);
				}
			}

			// For all the (dst, msg(s1), ..., msg(s(K-1))) in the map, output
			// updated messages.
			Iterator it = n_msg_map.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry pairs = (Map.Entry) it.next();

				long cur_dst = ((Long) pairs.getKey()).longValue();
				double[] next_msg = (double[]) pairs.getValue();// ((Double)pairs.getValue()).doubleValue();
				double[] old_msg = o_msg_map.get(cur_dst);

				double diff = 0;
				for (int s = 0; s < nstate; s++) {
					diff += Math.abs(next_msg[s] - old_msg[s]);
				}
				output.collect(new LongWritable(0), new Text("" + diff));
			}
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////
	// STAGE 2.3: Sum Error
	// - Input: current message (bp_message_cur) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)},
	// prior matrix(bp_prior) = {(nodeid, "p" + state1_prior, ..., "p" +
	// state(K-1)_prior)}
	// - Output: updated message (bp_message_next) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)}
	// //////////////////////////////////////////////////////////////////////////////////////////////
	public static class MapSumErr extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {

		// Identity mapper
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			final String[] line = value.toString().split("\t");

			String value_str = "";
			for (int i = 1; i < line.length; i++) {
				value_str += line[i];
				if (i < line.length - 1)
					value_str += "\t";
			}

			output.collect(new LongWritable(Long.parseLong(line[0])), new Text(value_str));
		}
	}

	public static class RedSumErr extends MapReduceBase implements
			Reducer<LongWritable, Text, LongWritable, Text> {
		int nstate = 2;

		@Override
		public void configure(JobConf job) {
			nstate = Integer.parseInt(job.get("nstate"));
		}

		@Override
		public void reduce(final LongWritable key, final Iterator<Text> values,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {

			long total_cnt = 0;
			double sum = 0;
			while (values.hasNext()) {
				String cur_value_str = values.next().toString();
				double diff = Double.parseDouble(cur_value_str);
				if(diff > NEPS * nstate){
					total_cnt ++;
				}
				sum += diff;
			}
			output.collect(new LongWritable(total_cnt), new Text("" + sum));
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////
	// STAGE 3: Compute Belief
	// - Input: current message (bp_message_cur) = {(src, dst, "s" +
	// state1_prob, ..., "s" + state(K-1)_prob)},
	// prior matrix(bp_prior) = {(nodeid, "p" + state1_prior, ..., "p" +
	// state(K-1)_prior)}
	// - Output: belief (bp_output) = {(nodeid, state1_belief, ...,
	// stateK_belief)}
	// //////////////////////////////////////////////////////////////////////////////////////////////
	public static class MapComputeBelief extends MapReduceBase implements
			Mapper<LongWritable, Text, LongWritable, Text> {
		// Identity mapper
		@Override
		public void map(final LongWritable key, final Text value,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			String input_line = value.toString();
			int tabpos = input_line.indexOf("\t");
			if (tabpos > 0) {
				output.collect(
						new LongWritable(Long.parseLong(input_line.substring(0,
								tabpos))),
						new Text(input_line.substring(tabpos + 1)));
			}
		}
	}

	public static class RedComputeBelief extends MapReduceBase implements
			Reducer<LongWritable, Text, LongWritable, Text> {
		double[][] ep;
		int nstate = 2;
		double threshold_underflow = 1e-50;
		double threshold_multiplier = 1e50;

		@Override
		public void configure(JobConf job) {
			nstate = Integer.parseInt(job.get("nstate"));
			String compat_matrix_str = job.get("compat_matrix_str");
			String[] tokens = compat_matrix_str.split("\t");

			ep = new double[nstate][nstate];
			int cur_seq = 0;
			int row, col;
			for (row = 0; row < nstate; row++) {
				double cumul_sum = 0;
				for (col = 0; col < nstate - 1; col++) {
					ep[row][col] = Double.parseDouble(tokens[cur_seq++]);
					cumul_sum += ep[row][col];
				}
				ep[row][col] = 1 - cumul_sum;
			}

			// System.out.println("[RedComputeBelief] nstate = " + nstate
			// + ". Compatibility Matrix=");
			// for (row = 0; row < nstate; row++) {
			// for (col = 0; col < nstate; col++) {
			// System.out.print("" + ep[row][col] + "\t");
			// }
			// System.out.println("");
			// }
		}

		@Override
		public void reduce(final LongWritable key, final Iterator<Text> values,
				final OutputCollector<LongWritable, Text> output,
				final Reporter reporter) throws IOException {
			double[] temp_s = new double[nstate];
			int i;

			for (i = 0; i < nstate; i++)
				temp_s[i] = 1;

			while (values.hasNext()) {
				String cur_value_str = values.next().toString();
				String[] tokens = cur_value_str.split("\t");
				// System.out.println("[DEBUG RedStage2] val=" + cur_value_str
				// );

				if (cur_value_str.startsWith("p")) { // prior vector
					double sum = 0;
					double cur_prior;
					for (i = 0; i < nstate - 1; i++) {
						cur_prior = Double.parseDouble(tokens[i].substring(1));
						temp_s[i] *= cur_prior;
						sum += cur_prior;
					}
					cur_prior = 1.0 - sum;
					temp_s[i] *= cur_prior;

					// System.out.println("NODE=" + key.get() + ", prior[0]=" +
					// prior_0 + ", prior[1]=" + (1-prior_0));
				} else { // message vector
					double sum = 0;
					for (i = 0; i < nstate - 1; i++) {
						double cur_state_prob = Double
								.parseDouble(tokens[i + 1].substring(1));

						temp_s[i] *= cur_state_prob;
						sum += cur_state_prob;
					}
					temp_s[i] *= (1.0 - sum);
					// System.out.println("NODE=" + key.get() + ", dst:" +
					// tokens[0] + ", cur_s1 = " + cur_s1 + ", cur_s2 = " +
					// (1-cur_s1) +", temp_s[0]=" + temp_s[0]+ ", temp_s[1]=" +
					// temp_s[1] );

					// avoid underflow.
					for (i = 0; i < nstate; i++) {
						if (temp_s[i] < threshold_underflow) {
							// System.out.println("[DEBUG] multiplying scalars!!!");
							int j;
							double max_val = 0;
							for (j = 0; j < nstate; j++)
								if (temp_s[j] > max_val)
									max_val = temp_s[j];

							double mult_val = threshold_multiplier / max_val;

							for (j = 0; j < nstate; j++)
								temp_s[j] *= mult_val;
							break;
						}
					}
				}
			}

			// It's ok that there is no prior vector for this node, since it
			// will be normalized anyway.

			// normalize beliefs
			double sum = 0;
			for (i = 0; i < nstate; i++)
				sum += temp_s[i];

			for (i = 0; i < nstate; i++)
				temp_s[i] /= sum;

			// System.out.println("FINAL NODE=" + key.get() + ", temp_s[0]=" +
			// temp_s[0] + ", temp_s[1]=" + temp_s[1] );
			String out_val = "";

			for (i = 0; i < nstate; i++) {
				if (i > 0)
					out_val += "\t";
				out_val += temp_s[i];
			}

			output.collect(key, new Text(out_val));
		}
	}

	// ////////////////////////////////////////////////////////////////////
	// command line interface
	// ////////////////////////////////////////////////////////////////////
	protected Path edge_path = null;
	protected Path prior_path = null;
	protected Path message_cur_path = new Path("run_tmp/bp_message_cur");
	protected Path message_next_path = new Path("run_tmp/bp_message_next");
	protected Path check_error_path = new Path("run_tmp/bp_error_check");
	protected Path sum_error_path = new Path("run_tmp/bp_error_check_sum");
	protected Path output_path = null;
	protected long number_msg = 0;
	protected int max_iter = 32;
	protected int nreducer = 1;
	protected int nstate = 2;
	String edge_potential_str = "";
	FileSystem fs;

	// Main entry point.
	public static void main(final String[] args) throws Exception {
		final int result = ToolRunner.run(new Configuration(), new BP(), args);

		System.exit(result);
	}

	// Print the command-line usage text.
	protected static int printUsage() {
		System.out
				.println("BeliefPropagation <edge_path> <prior_path> <output_path> <# of nodes>  <# of reducer> <max iteration> <makesym or nosym> <number of state> <edge potential> <newmsg or contNN>");

		ToolRunner.printGenericCommandUsage(System.out);

		return -1;
	}

	public String read_edge_potential(String input_file) {
		String result_str = "";
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					new FileInputStream(input_file), "UTF8"));

			String cur_str = "";
			while (cur_str != null) {
				cur_str = in.readLine();
				if (cur_str != null) {
					if (result_str.length() > 0)
						result_str += "\t";
					result_str += cur_str;
				}
			}
			in.close();
		} catch (UnsupportedEncodingException e) {
		} catch (IOException e) {
		}

		System.out.println("EDGE_POTENTIAL_STR = [" + result_str + "]");

		return result_str;
	}

	protected static void copyToLocalFile(Configuration conf, Path hdfs_path,
			Path local_path) throws Exception {
		FileSystem fs = FileSystem.get(conf);

		// read the result
		fs.copyToLocalFile(hdfs_path, local_path);
	}

	protected static String readLocaldirOneline(String new_path) throws Exception
	{
		String output_path = new_path + "/part-00000";
		String str = "";
		try {
			BufferedReader in = new BufferedReader(
				new InputStreamReader(new FileInputStream( output_path ), "UTF8"));
			str = in.readLine();
			in.close();
		} catch (UnsupportedEncodingException e) {
		} catch (IOException e) {
		}

		return str;
	}

	private static final double EPS = 1e-6;
	private static final double NEPS = 1e-5;

	// submit the map/reduce job.
	@Override
	public int run(String[] args) throws Exception {
		if (args.length != 10) {
			for (int i = 0; i < args.length; i++) {
				System.out.println("Args: " + i + " " + args[i]);
			}
			System.out.println(args.length);
			return printUsage();
		}

		/*
		 * String[] newargs = (String[])Array.newInstance(String.class,
		 * args.length - 1); for(int i=0; i<args.length-1; ++i){ newargs[i] =
		 * args[i+1]; } args = newargs;
		 */
		edge_path = new Path(args[0]);
		prior_path = new Path(args[1]);
		output_path = new Path(args[2]);
		Path prev_local_path = new Path("run_tmp/prev_local/");
		Path new_local_path = new Path("run_tmp/new_local/");
		Path tmp_output_path = new Path(output_path.toString());

		number_msg = Long.parseLong(args[3]);
		nreducer = Integer.parseInt(args[4]);
		nreducer = 1;
		max_iter = Integer.parseInt(args[5]);

		nstate = Integer.parseInt(args[7]);
		edge_potential_str = read_edge_potential(args[8]);

		int cur_iter = 1;
		if (args[9].startsWith("new") == false) {
			cur_iter = Integer.parseInt(args[9].substring(4));
		}

		System.out.println("edge_path=" + edge_path.toString()
				+ ", prior_path=" + prior_path.toString() + ", output_path="
				+ output_path.toString() + ", |E|=" + number_msg
				+ ", nreducer=" + nreducer + ", maxiter=" + max_iter
				+ ", nstate=" + nstate + ", edge_potential_str="
				+ edge_potential_str + ", cur_iter=" + cur_iter);

		fs = FileSystem.get(getConf());

		// Run Stage1 and Stage2.
		if (cur_iter == 1) {
			System.out.println("BP: Initializing messages...");
			JobClient.runJob(configInitMessage());
		}

		double converge_threshold = number_msg * EPS * nstate;

		int i;
		for (i = cur_iter; i <= max_iter; i++) {
//			System.out.println("   *** ITERATION " + (i) + "/" + max_iter
//					+ " ***");

			JobClient.runJob(configUpdateMessage());
			JobClient.runJob(configCheckErr());
			JobClient.runJob(configSumErr());
			String line = readLocaldirOneline(sum_error_path.toString());
			fs.delete(check_error_path, true);
			fs.delete(sum_error_path, true);
			String[] parts = line.split("\t");
			int n = Integer.parseInt(parts[0]);
			double sum = Double.parseDouble(parts[1]);
			System.out.println("Converged Msg: " + (number_msg - n));
			System.out.println("Sum Error: " + sum);
			if(sum < converge_threshold){
				break;
			}

			// rotate directory
			fs.delete(message_cur_path);
			fs.rename(message_next_path, message_cur_path);
		}
		System.out.println("CONVERGE_ITER " + i);
		System.out.println("BP: Computing beliefs...");
		JobClient.runJob(configComputeBelief());

		System.out.println("BP finished. The belief vector is in the HDFS "
				+ args[2]);

		return 0;
	}

	// Configure pass1
	protected JobConf configInitMessage() throws Exception {
		final JobConf conf = new JobConf(getConf(), BP.class);
		conf.set("nstate", "" + nstate);
		conf.set("compat_matrix_str", "" + edge_potential_str);
		conf.setJobName("BP_Init_Belief");

		conf.setMapperClass(MapInitializeBelief.class);

		fs.delete(message_cur_path, true);

		FileInputFormat.setInputPaths(conf, edge_path);
		FileOutputFormat.setOutputPath(conf, message_cur_path);

		conf.setNumReduceTasks(0);

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);
		return conf;
	}

	// Configure pass2
	protected JobConf configUpdateMessage() throws Exception {
		final JobConf conf = new JobConf(getConf(), BP.class);
		conf.set("nstate", "" + nstate);
		conf.set("compat_matrix_str", "" + edge_potential_str);
		conf.setJobName("BP_Update_message");

		conf.setMapperClass(MapUpdateMessage.class);
		conf.setReducerClass(RedUpdateMessage.class);

		fs.delete(message_next_path, true);

		FileInputFormat.setInputPaths(conf, message_cur_path, prior_path);
		FileOutputFormat.setOutputPath(conf, message_next_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

	protected JobConf configCheckErr() throws Exception {
		final JobConf conf = new JobConf(getConf(), BP.class);
		conf.set("nstate", "" + nstate);
		conf.setJobName("BP_Check Err");

		fs.delete(check_error_path, true);

		conf.setMapperClass(MapCheckErr.class);
		conf.setReducerClass(RedCheckErr.class);

		FileInputFormat.setInputPaths(conf, message_cur_path, message_next_path);
		FileOutputFormat.setOutputPath(conf, check_error_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

	protected JobConf configSumErr() throws Exception {
		final JobConf conf = new JobConf(getConf(), BP.class);
		conf.set("nstate", "" + nstate);
		conf.setJobName("BP_Sum Err");

		fs.delete(sum_error_path, true);

		conf.setMapperClass(MapSumErr.class);
		conf.setReducerClass(RedSumErr.class);

		FileInputFormat.setInputPaths(conf, check_error_path);
		FileOutputFormat.setOutputPath(conf, sum_error_path);

		conf.setNumReduceTasks(1);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

	protected JobConf configComputeBelief() throws Exception {
		final JobConf conf = new JobConf(getConf(), BP.class);
		conf.set("nstate", "" + nstate);
		conf.set("compat_matrix_str", "" + edge_potential_str);
		conf.setJobName("BP_Compute_Belief");

		conf.setMapperClass(MapComputeBelief.class);
		conf.setReducerClass(RedComputeBelief.class);

		fs.delete(output_path, true);

		FileInputFormat.setInputPaths(conf, message_cur_path, prior_path);
		FileOutputFormat.setOutputPath(conf, output_path);

		conf.setNumReduceTasks(nreducer);

		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(Text.class);

		return conf;
	}

}
