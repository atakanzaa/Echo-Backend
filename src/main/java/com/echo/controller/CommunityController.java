package com.echo.controller;

import com.echo.dto.request.CreateCommentRequest;
import com.echo.dto.request.CreatePostRequest;
import com.echo.dto.response.CommunityPostResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.dto.response.PostCommentResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    // ── Feed ──────────────────────────────────────────────────────────────────

    @GetMapping("/feed")
    public ResponseEntity<PagedResponse<CommunityPostResponse>> getFeed(
            @RequestParam(defaultValue = "global") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.getFeed(principal.getId(), tab, page, Math.min(size, 50)));
    }

    // ── Single Post ───────────────────────────────────────────────────────────

    @GetMapping("/posts/{postId}")
    public ResponseEntity<CommunityPostResponse> getPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.getPost(postId, principal.getId()));
    }

    // ── Create Post (JSON — text only) ────────────────────────────────────────

    @PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommunityPostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.createPost(principal.getId(), request, null));
    }

    // ── Create Post (Multipart — with image) ─────────────────────────────────

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommunityPostResponse> createPostWithImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "emoji", required = false) String emoji,
            @RequestParam(value = "isAnonymous", defaultValue = "false") boolean isAnonymous,
            @RequestParam(value = "badgeKey", required = false) String badgeKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        CreatePostRequest request = new CreatePostRequest(content, "image", emoji, isAnonymous, badgeKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.createPost(principal.getId(), request, image));
    }

    // ── Delete Post ───────────────────────────────────────────────────────────

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.deletePost(postId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Post Like / Unlike ────────────────────────────────────────────────────

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Void> likePost(@PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.likePost(postId, principal.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlikePost(@PathVariable UUID postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.unlikePost(postId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<PagedResponse<PostCommentResponse>> getComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.getComments(postId, principal.getId(), page, size));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<PostCommentResponse> createComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communityService.createComment(postId, principal.getId(), request));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.deleteComment(postId, commentId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Comment Like / Unlike ─────────────────────────────────────────────────

    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<Void> likeComment(@PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.likeComment(commentId, principal.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ResponseEntity<Void> unlikeComment(@PathVariable UUID commentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.unlikeComment(commentId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Follow / Unfollow ─────────────────────────────────────────────────────

    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<Void> followUser(@PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.followUser(principal.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<Void> unfollowUser(@PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.unfollowUser(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }
}
