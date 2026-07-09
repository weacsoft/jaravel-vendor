@extends('layout')

@section('title', 'My Page')

@section('content')
    <h1>Welcome, {{ $name }}!</h1>
    <p>This is the page content.</p>
@endsection
