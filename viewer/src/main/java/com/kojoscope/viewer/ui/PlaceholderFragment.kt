package com.kojoscope.viewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kojoscope.viewer.R

class PlaceholderFragment : Fragment() {

    private var message: String = ""

    companion object {
        fun newInstance(message: String): PlaceholderFragment {
            val f = PlaceholderFragment()
            f.message = message
            return f
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val text = view.findViewById<TextView>(R.id.placeholderText)
        if (message.isNotEmpty()) text.text = message
    }
}