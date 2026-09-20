package com.example.myapplication.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.example.myapplication.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FullScreenImageViewerDialog(
    private val activity: Activity,
    private val images: List<String>,
    private val startPosition: Int = 0
) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var container: ViewGroup
    private lateinit var viewPager: ViewPager2
    private lateinit var tvCounter: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var llDots: LinearLayout

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_fullscreen_image_viewer)

        window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                w.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
            }
        }

        initViews()
        setupViewPager()
        setupSwipeDownToDismiss()
    }

    private fun initViews() {
        container = findViewById(R.id.flFullscreenContainer)
        viewPager = findViewById(R.id.vpFullscreenImages)
        tvCounter = findViewById(R.id.tvFullscreenCounter)
        btnClose = findViewById(R.id.btnFullscreenClose)
        llDots = findViewById(R.id.llFullscreenDots)

        btnClose.setOnClickListener {
            dismissWithAnimation()
        }

        updateCounter(startPosition)
        setupDots(images.size, startPosition)
    }

    private fun setupViewPager() {
        viewPager.layoutDirection = View.LAYOUT_DIRECTION_LTR
        viewPager.adapter = FullscreenImageAdapter(images)
        viewPager.setCurrentItem(startPosition, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCounter(position)
                updateDots(position)
            }
        })
    }

    private fun updateCounter(position: Int) {
        if (images.size > 1) {
            tvCounter.visibility = View.VISIBLE
            tvCounter.text = "${position + 1} / ${images.size}"
        } else {
            tvCounter.visibility = View.GONE
        }
    }

    private fun setupDots(count: Int, activePosition: Int) {
        llDots.removeAllViews()
        if (count <= 1) {
            llDots.visibility = View.GONE
            return
        }
        llDots.visibility = View.VISIBLE
        val density = activity.resources.displayMetrics.density
        val dotSize = (7 * density).toInt()
        val dotMargin = (3 * density).toInt()

        for (i in 0 until count) {
            val dot = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    marginStart = dotMargin
                    marginEnd = dotMargin
                }
                setBackgroundResource(
                    if (i == activePosition) R.drawable.bg_carousel_dot_active
                    else R.drawable.bg_carousel_dot_inactive
                )
                scaleX = if (i == activePosition) 1.25f else 1.0f
                scaleY = if (i == activePosition) 1.25f else 1.0f
                setOnClickListener { viewPager.setCurrentItem(i, true) }
            }
            llDots.addView(dot)
        }
    }

    private fun updateDots(activePosition: Int) {
        for (i in 0 until llDots.childCount) {
            val dot = llDots.getChildAt(i)
            val isActive = (i == activePosition)
            dot.setBackgroundResource(
                if (isActive) R.drawable.bg_carousel_dot_active
                else R.drawable.bg_carousel_dot_inactive
            )
            dot.animate()
                .scaleX(if (isActive) 1.25f else 1.0f)
                .scaleY(if (isActive) 1.25f else 1.0f)
                .setDuration(200)
                .start()
        }
    }

    // ── Smooth Swipe Down to Dismiss ──────────────────────────────────────────

    private fun setupSwipeDownToDismiss() {
        val density = activity.resources.displayMetrics.density
        val dismissThreshold = 130 * density
        var initialY = 0f
        var initialX = 0f
        var isDraggingDown = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    isDraggingDown = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - initialY
                    val dx = event.rawX - initialX
                    if (dy > 20 && dy > abs(dx) * 1.5f && !isDraggingDown) {
                        isDraggingDown = true
                        viewPager.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDraggingDown && dy > 0) {
                        container.translationY = dy
                        val alpha = max(0.3f, 1f - (dy / (activity.resources.displayMetrics.heightPixels * 0.8f)))
                        container.alpha = alpha
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingDown) {
                        val dy = event.rawY - initialY
                        if (dy > dismissThreshold) {
                            animateDismiss(dy)
                        } else {
                            // Spring back smoothly
                            container.animate()
                                .translationY(0f)
                                .alpha(1f)
                                .setDuration(200)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                        }
                        isDraggingDown = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun animateDismiss(currentY: Float) {
        val screenHeight = activity.resources.displayMetrics.heightPixels.toFloat()
        container.animate()
            .translationY(screenHeight)
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dismiss()
                }
            })
            .start()
    }

    private fun dismissWithAnimation() {
        container.animate()
            .alpha(0f)
            .setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dismiss()
                }
            })
            .start()
    }

    // ── Fullscreen Adapter with Double-Tap Zoom ───────────────────────────────

    private inner class FullscreenImageAdapter(
        private val urls: List<String>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<FullscreenImageAdapter.VH>() {

        inner class VH(val root: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root) {
            val iv: ImageView = root.findViewById(R.id.ivFullscreenImage)
            val pb: ProgressBar = root.findViewById(R.id.pbFullscreenLoading)
            var currentScale = 1.0f
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_fullscreen_image, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.pb.visibility = View.VISIBLE
            holder.iv.scaleX = 1f
            holder.iv.scaleY = 1f
            holder.currentScale = 1.0f

            Glide.with(holder.iv.context)
                .load(urls[position])
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(withCrossFade(200))
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        holder.pb.visibility = View.GONE
                        holder.iv.setImageDrawable(resource)
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        holder.iv.setImageDrawable(placeholder)
                    }

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        holder.pb.visibility = View.GONE
                        holder.iv.setImageResource(R.drawable.ic_photo_placeholder)
                    }
                })

            // Double tap to zoom smoothly (1.0x <-> 2.2x, matching iOS)
            val gestureDetector = GestureDetector(holder.iv.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val targetScale = if (holder.currentScale > 1.1f) 1.0f else 2.2f
                    holder.iv.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(220)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    holder.currentScale = targetScale
                    return true
                }
            })

            holder.iv.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }

        override fun getItemCount(): Int = urls.size
    }
}
