package org.epitest.launcher;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class Arguments {

	private final List<String> args = new ArrayList<String>();

	Arguments add(String attribute, String value) {
		args.add(checkNotNull(attribute));
		args.add(checkNotNull(value));
		return this;
	}

	public Arguments add(String attribute, boolean b) {
		return add(attribute, Boolean.toString(b));
	}

	public Arguments add(String attribute, Number value) {
		return add(attribute, value.toString());
	}

	Arguments add(String attribute, Collection<String> values) {
		return add(attribute, on(',').join(values));
	}

	String[] toArray() {
		return args.toArray(new String[args.size()]);
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		final int size = args.size()/2;
		for (int i = 0; i < size; i++) {
			if (i>0)
				b.append(',');
			b.append(args.get(i*2));
			b.append('=');
			b.append(args.get(i*2+1));
		}
		return b.toString();
	}
}